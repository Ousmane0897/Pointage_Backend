package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.exception.StockInsuffisantException;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;

/**
 * Contre-passement des mouvements d'une pièce de stock annulée, bon ou inventaire (lot 1 / 1b, D4) : pour chaque mouvement du
 * bon, un mouvement <b>inverse</b> ({@code origine = "ANNULATION"}, {@code mouvementAnnuleId})
 * et le solde rétabli ; le mouvement d'origine est marqué {@code contrePasse = true}. Rien n'est
 * supprimé : l'historique garde l'aller et le retour.
 *
 * <ul>
 *   <li>bon d'entrée annulé : débit <b>gardé</b> de l'entrepôt (seul crédité à la réception) ;</li>
 *   <li>bon de sortie annulé : recrédit selon la répartition du débit (site, puis entrepôt),
 *       ou l'entrepôt pour un mouvement antérieur à 1b (comme l'ancienne suppression
 *       définitive) ;</li>
 *   <li>inventaire annulé : ajustements opposés sur le site de l'inventaire, non gardés,
 *       comme la clôture.</li>
 * </ul>
 * Le coût courant (CUMP) n'est pas restauré, comme avant 1b ; le mouvement inverse reprend le
 * coût du mouvement annulé.
 */
@Service
@RequiredArgsConstructor
public class ContrePassementStockService {

    private final MouvementStockRepository mouvementRepository;
    private final StockBalanceService balanceService;
    private final CompteurStockService compteurService;
    private final Clock clock;

    /**
     * Garde synchrone de l'annulation d'un bon d'entrée, dans sa transaction : la marchandise reçue
     * doit être encore en stock, sinon 422 {@code STOCK_INSUFFISANT} (le contre-passement
     * échouerait ensuite à chaque essai).
     */
    public void verifierRetraitPossible(String bonId) {
        Map<String, Double> cumul = new LinkedHashMap<>();
        Map<String, String> codes = new LinkedHashMap<>();
        for (MouvementStock m : aContrePasser(bonId)) {
            cumul.merge(m.getProduitId(), m.getQuantite(), Double::sum);
            codes.putIfAbsent(m.getProduitId(), m.getProduitCode());
        }
        cumul.forEach((produitId, aRetirer) -> {
            double disponible = balanceService.quantite(produitId, ENTREPOT);
            if (disponible < aRetirer) {
                throw new StockInsuffisantException("Annulation impossible pour " + codes.get(produitId)
                        + " : la marchandise reçue a déjà été consommée (disponible " + disponible
                        + ", à retirer " + aRetirer + "). Ajustez le stock avant d'annuler ce bon.");
            }
        });
    }

    /** Contre-passe les mouvements du bon (dans la transaction du consommateur). Idempotent. */
    public int contrePasserBon(String bonId, String motif, String auteur) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Contre-passement hors transaction");
        }
        List<MouvementStock> mouvements = aContrePasser(bonId);
        for (MouvementStock m : mouvements) {
            if (m.getType() == TypeMouvement.ENTREE) {
                balanceService.debiter(m.getProduitId(), ENTREPOT, m.getQuantite());
            } else {
                recrediterSortie(m);
            }
            MouvementStock inverse = inverse(m, motif, auteur);
            mouvementRepository.save(inverse);
            m.setContrePasse(true);
            mouvementRepository.save(m);
        }
        return mouvements.size();
    }

    /**
     * Contre-passe les ajustements d'un inventaire clôturé (dans la transaction du consommateur).
     * Les ajustements antérieurs au rattachement par {@code inventaireId} sont retrouvés par leur
     * commentaire, comme le faisait l'ancienne suppression définitive. Idempotent.
     */
    public int contrePasserInventaire(String inventaireId, String reference, String motif, String auteur) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Contre-passement hors transaction");
        }
        List<MouvementStock> ajustements = mouvementRepository.findByInventaireId(inventaireId);
        if (ajustements.isEmpty() && reference != null) {
            ajustements = mouvementRepository.findByCommentaire("Ajustement inventaire " + reference).stream()
                    .filter(m -> m.getMotif() == MotifMouvement.AJUSTEMENT)
                    .toList();
        }
        List<MouvementStock> aTraiter = ajustements.stream()
                .filter(m -> !MouvementsEffectifs.ORIGINE_ANNULATION.equals(m.getOrigine()))
                .filter(m -> !Boolean.TRUE.equals(m.getContrePasse()))
                .toList();
        for (MouvementStock m : aTraiter) {
            boolean entree = m.getType() == TypeMouvement.ENTREE;
            String site = entree ? m.getSiteDestinationId() : m.getSiteSourceId();
            balanceService.appliquerDelta(m.getProduitId(), site, entree ? -m.getQuantite() : m.getQuantite());
            MouvementStock inverse = inverse(m, motif, auteur);
            inverse.setInventaireId(inventaireId);
            inverse.setInventaireReference(m.getInventaireReference() != null ? m.getInventaireReference() : reference);
            mouvementRepository.save(inverse);
            m.setContrePasse(true);
            mouvementRepository.save(m);
        }
        return aTraiter.size();
    }

    private List<MouvementStock> aContrePasser(String bonId) {
        return mouvementRepository.findByBonId(bonId).stream()
                .filter(m -> "BON".equals(m.getOrigine()))
                .filter(m -> !Boolean.TRUE.equals(m.getContrePasse()))
                .toList();
    }

    private void recrediterSortie(MouvementStock m) {
        if (m.getQuantiteSurSite() == null && m.getQuantiteSurConsolide() == null) {
            balanceService.appliquerDelta(m.getProduitId(), ENTREPOT, m.getQuantite());
            return;
        }
        double surSite = m.getQuantiteSurSite() == null ? 0.0 : m.getQuantiteSurSite();
        double surConsolide = m.getQuantiteSurConsolide() == null ? 0.0 : m.getQuantiteSurConsolide();
        if (surSite > 0) {
            balanceService.appliquerDelta(m.getProduitId(), m.getSiteSourceId(), surSite);
        }
        if (surConsolide > 0) {
            balanceService.appliquerDelta(m.getProduitId(), ENTREPOT, surConsolide);
        }
    }

    private MouvementStock inverse(MouvementStock m, String motif, String auteur) {
        boolean entree = m.getType() == TypeMouvement.ENTREE;
        return MouvementStock.builder()
                .reference(compteurService.genererReference("MVT"))
                .produitId(m.getProduitId())
                .produitCode(m.getProduitCode())
                .produitLibelle(m.getProduitLibelle())
                .unite(m.getUnite())
                .type(entree ? TypeMouvement.SORTIE : TypeMouvement.ENTREE)
                .motif(MotifMouvement.AJUSTEMENT)
                .quantite(m.getQuantite())
                // Le retour se fait là d'où le mouvement venait : sites échangés.
                .siteSourceId(m.getSiteDestinationId())
                .siteSourceNom(m.getSiteDestinationNom())
                .siteDestinationId(m.getSiteSourceId())
                .siteDestinationNom(m.getSiteSourceNom())
                .date(LocalDate.now(clock))
                .utilisateur(auteur)
                .commentaire("Annulation de " + (m.getBonReference() != null ? m.getBonReference()
                        : m.getInventaireReference()) + (motif == null ? "" : " : " + motif))
                .origine(MouvementsEffectifs.ORIGINE_ANNULATION)
                .bonId(m.getBonId())
                .bonReference(m.getBonReference())
                .mouvementAnnuleId(m.getId())
                .coutUnitaireSnapshot(m.getCoutUnitaireSnapshot())
                .valeurMouvement(m.getValeurMouvement())
                .createdAt(LocalDateTime.now(clock))
                .build();
    }
}
