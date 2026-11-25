package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.example.Pointage_Cleanic.entities.stockv2.HistoriquePointCout;
import com.example.Pointage_Cleanic.entities.stockv2.Inventaire;
import com.example.Pointage_Cleanic.entities.stockv2.LigneInventaire;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.SuppressionStockLog;
import com.example.Pointage_Cleanic.entities.stockv2.SuppressionStockLog.LigneContrePassee;
import com.example.Pointage_Cleanic.entities.stockv2.SuppressionStockLog.TypeDocument;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.StockAccesRefuseException;
import com.example.Pointage_Cleanic.exception.StockOperationException;
import com.example.Pointage_Cleanic.repositories.stockv2.InventaireRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.SuppressionStockLogRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;

/**
 * Suppression définitive d'un document de stock déjà engagé — <b>super-administrateur uniquement</b>.
 *
 * <p>Depuis le lot 1 / 1b, seuls les inventaires passent encore ici (les bons s'annulent :
 * {@code BonEntreeService.annuler}, {@code BonSortieService.annuler}) ; le service disparaît en
 * B8 avec l'annulation des inventaires.
 *
 * <p>Un inventaire clôturé et un bon effectif ont déjà touché le stock : les supprimer sans rien
 * faire d'autre laisserait des mouvements orphelins et des soldes faux. Chaque suppression
 * <b>contre-passe</b> donc l'effet stock du document, supprime les mouvements générés, journalise
 * l'opération ({@link SuppressionStockLog}) puis efface le document.
 *
 * <p>Tous les statuts sont acceptés : sur un document qui n'a rien mouvementé (brouillon, soumis,
 * refusé, inventaire non clôturé) le contre-passement est un no-op — inutile de refuser en 409,
 * l'appelant est déjà super-administrateur.
 *
 * <p><b>Le contre-passement des bons vise l'{@link StockBalanceService#ENTREPOT}</b>, seul lieu de
 * stockage du métier : {@link MouvementBonGenerator#genererPourEntree} y crédite tout ce qui entre
 * (quel que soit le site de destination documenté sur le bon) et
 * {@link MouvementBonGenerator#genererPourSortie} en sort tout via {@code debiterAvecRepli}, les
 * soldes par site n'étant plus alimentés. Viser le site du mouvement créerait du stock fantôme sur
 * un site tout en laissant l'entrepôt faux. Sur des données historiques dont le débit avait pu
 * entamer un solde de site, le recrédit atterrit sur l'entrepôt : le total consolidé du produit
 * reste exact, et c'est le sens de la consolidation opérée par
 * la migration {@code 0007-stock-entrepot-unique} ({@code StockEntrepotUniqueMigration}).
 *
 * <p><b>Limite assumée</b> : le coût courant d'un produit (CUMP) n'est pas restauré lors de la
 * suppression d'un bon d'entrée : il faudrait le coût d'avant l'entrée, que seule la transaction
 * d'origine connaissait. Seuls les points d'historique de coût sont nettoyés. (Service remplacé par
 * l'annulation tracée en 1b, décision D4.)
 */
@Service
@RequiredArgsConstructor
public class SuppressionDefinitiveService {

    /** Longueur minimale du motif, alignée sur le refus de congé. */
    public static final int MOTIF_MIN = 10;

    private final InventaireRepository inventaireRepository;
    private final MouvementStockRepository mouvementRepository;
    private final SuppressionStockLogRepository logRepository;
    private final StockBalanceService balanceService;
    private final CurrentUserProvider currentUser;
    private final HabilitationStock habilitation;

    // ─── Inventaire ─────────────────────────────────────────────────────────

    public void supprimerInventaire(String id, String motif) {
        exigerSuperAdmin();
        String motifNet = exigerMotif(motif);
        Inventaire inv = inventaireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventaire introuvable : " + id));

        List<LigneContrePassee> contrePassees = new ArrayList<>();
        List<MouvementStock> mouvements = List.of();

        if (inv.getStatut() == StatutInventaire.CLOTURE) {
            mouvements = mouvementsDeLInventaire(inv);
            // La source de vérité est le document lui-même : l'inverse exact de ce qu'a appliqué
            // InventaireService.cloturer (+ecart par ligne, sur le site de l'inventaire).
            // ⚠ On lit le même inv.getSiteId() que la clôture, donc la symétrie tient quel que
            // soit ce site — y compris si la clôture bascule un jour sur l'entrepôt unique, ce
            // qu'elle n'a pas fait (seuls les bons y sont passés).
            for (LigneInventaire ligne : inv.getLignes()) {
                double ecart = ligne.getEcart() == null ? 0.0 : ligne.getEcart();
                if (ecart == 0.0) {
                    continue;
                }
                balanceService.appliquerDelta(ligne.getProduitId(), inv.getSiteId(), -ecart);
                contrePassees.add(LigneContrePassee.builder()
                        .produitId(ligne.getProduitId())
                        .produitCode(ligne.getProduitCode())
                        .delta(-ecart)
                        .siteId(inv.getSiteId())
                        .build());
            }
            supprimerMouvements(mouvements);
        }

        journaliser(TypeDocument.INVENTAIRE, inv.getId(), inv.getReference(),
                String.valueOf(inv.getStatut()), motifNet, mouvements.size(), contrePassees);
        inventaireRepository.deleteById(inv.getId());
    }

    /**
     * Ajustements de clôture : par {@code inventaireId} depuis ce lot, sinon repli sur le commentaire
     * pour les inventaires clôturés avant l'ajout du champ.
     */
    private List<MouvementStock> mouvementsDeLInventaire(Inventaire inv) {
        List<MouvementStock> parId = mouvementRepository.findByInventaireId(inv.getId());
        if (!parId.isEmpty()) {
            return parId;
        }
        return mouvementRepository.findByCommentaire("Ajustement inventaire " + inv.getReference()).stream()
                .filter(m -> m.getMotif() == MotifMouvement.AJUSTEMENT)
                .toList();
    }

    // ─── Briques communes ───────────────────────────────────────────────────

    private void exigerSuperAdmin() {
        habilitation.exigerSuperAdmin("Suppression définitive");
    }

    private String exigerMotif(String motif) {
        String net = motif == null ? "" : motif.trim();
        if (net.length() < MOTIF_MIN) {
            throw new IllegalArgumentException(
                    "Le motif de suppression est obligatoire (" + MOTIF_MIN + " caractères minimum)");
        }
        return net;
    }

    private void supprimerMouvements(List<MouvementStock> mouvements) {
        if (!mouvements.isEmpty()) {
            mouvementRepository.deleteAll(mouvements);
        }
    }

    private void journaliser(TypeDocument type, String documentId, String reference, String statutAvant,
                             String motif, int nbMouvements, List<LigneContrePassee> lignes) {
        logRepository.save(SuppressionStockLog.builder()
                .typeDocument(type)
                .documentId(documentId)
                .reference(reference)
                .statutAvant(statutAvant)
                .motif(motif)
                .auteurId(currentUser.currentUserId())
                .auteurNom(currentUser.currentUserNom())
                .dateSuppression(LocalDateTime.now())
                .nbMouvementsContrePasses(nbMouvements)
                .lignes(lignes)
                .build());
    }
}
