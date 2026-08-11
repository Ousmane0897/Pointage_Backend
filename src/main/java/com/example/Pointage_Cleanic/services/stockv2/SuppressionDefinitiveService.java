package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
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
import com.example.Pointage_Cleanic.repositories.stockv2.BonEntreeRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.BonSortieRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.HistoriquePointCoutRepository;
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

/**
 * Suppression définitive d'un document de stock déjà engagé — <b>super-administrateur uniquement</b>.
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
 * <p><b>Limites assumées</b> :
 * <ul>
 *   <li>la répartition site / bucket consolidé d'un débit de sortie n'est pas persistée
 *       ({@code RepartitionDebit} ne vit que le temps de {@link MouvementBonGenerator#genererPourSortie}) :
 *       le recrédit va intégralement sur le site source du mouvement. Le total consolidé du produit
 *       est exact, mais un reliquat prélevé sur le bucket {@code siteId = null} se retrouve sur le site ;</li>
 *   <li>le coût courant d'un produit (CUMP) n'est pas restauré lors de la suppression d'un bon
 *       d'entrée : {@link ValorisationSupport#compenserEntree} exige le {@code RecalcResult} de la
 *       transaction d'origine. Seuls les points d'historique de coût sont nettoyés.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SuppressionDefinitiveService {

    /**
     * ⚠ Le super-administrateur est la chaîne {@code SUPERADMIN}, sans underscore : c'est la seule
     * valeur réellement émise (collection {@code login}). Constante locale à stockv2 pour ne pas
     * dépendre du module RH.
     */
    public static final String ROLE_SUPERADMIN = "SUPERADMIN";

    /** Longueur minimale du motif, alignée sur le refus de congé. */
    public static final int MOTIF_MIN = 10;

    private final InventaireRepository inventaireRepository;
    private final BonEntreeRepository bonEntreeRepository;
    private final BonSortieRepository bonSortieRepository;
    private final MouvementStockRepository mouvementRepository;
    private final HistoriquePointCoutRepository historiqueCoutRepository;
    private final SuppressionStockLogRepository logRepository;
    private final StockBalanceService balanceService;
    private final CurrentUserProvider currentUser;

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

    // ─── Bon de sortie ──────────────────────────────────────────────────────

    public void supprimerBonSortie(String id, String motif) {
        exigerSuperAdmin();
        String motifNet = exigerMotif(motif);
        BonSortie bon = bonSortieRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bon de sortie introuvable : " + id));

        List<LigneContrePassee> contrePassees = new ArrayList<>();
        List<MouvementStock> mouvements = List.of();

        if (bon.getStatut() == StatutBon.EFFECTIF) {
            mouvements = mouvementRepository.findByBonId(bon.getId());
            for (MouvementStock mvt : mouvements) {
                // Recrédit du site source du mouvement (cf. limite documentée en tête de classe).
                balanceService.appliquerDelta(mvt.getProduitId(), mvt.getSiteSourceId(), mvt.getQuantite());
                contrePassees.add(ligneDe(mvt, mvt.getQuantite(), mvt.getSiteSourceId()));
            }
            supprimerMouvements(mouvements);
        }

        journaliser(TypeDocument.BON_SORTIE, bon.getId(), bon.getReference(),
                String.valueOf(bon.getStatut()), motifNet, mouvements.size(), contrePassees);
        bonSortieRepository.deleteById(bon.getId());
    }

    // ─── Bon d'entrée ───────────────────────────────────────────────────────

    public void supprimerBonEntree(String id, String motif) {
        exigerSuperAdmin();
        String motifNet = exigerMotif(motif);
        BonEntree bon = bonEntreeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'entrée introuvable : " + id));

        List<LigneContrePassee> contrePassees = new ArrayList<>();
        List<MouvementStock> mouvements = List.of();

        if (bon.getStatut() == StatutBon.EFFECTIF) {
            mouvements = mouvementRepository.findByBonId(bon.getId());
            verifierRetraitPossible(mouvements);
            for (MouvementStock mvt : mouvements) {
                balanceService.appliquerDelta(mvt.getProduitId(), mvt.getSiteDestinationId(), -mvt.getQuantite());
                contrePassees.add(ligneDe(mvt, -mvt.getQuantite(), mvt.getSiteDestinationId()));
            }
            nettoyerHistoriqueCout(mouvements);
            supprimerMouvements(mouvements);
        }

        journaliser(TypeDocument.BON_ENTREE, bon.getId(), bon.getReference(),
                String.valueOf(bon.getStatut()), motifNet, mouvements.size(), contrePassees);
        bonEntreeRepository.deleteById(bon.getId());
    }

    /**
     * Retirer une entrée déjà consommée rendrait le solde négatif — une donnée fausse qui se
     * propagerait aux statuts de rupture et à la valorisation. On refuse en 422 avec le détail :
     * au super-administrateur d'ajuster le stock d'abord (message actionnable), plutôt que de
     * laisser l'application produire des quantités négatives.
     */
    private void verifierRetraitPossible(List<MouvementStock> mouvements) {
        Map<String, Double> cumulParCouple = new LinkedHashMap<>();
        for (MouvementStock mvt : mouvements) {
            cumulParCouple.merge(mvt.getProduitId() + "|" + mvt.getSiteDestinationId(), mvt.getQuantite(), Double::sum);
        }
        for (MouvementStock mvt : mouvements) {
            String cle = mvt.getProduitId() + "|" + mvt.getSiteDestinationId();
            Double aRetirer = cumulParCouple.remove(cle);
            if (aRetirer == null) {
                continue;   // couple déjà vérifié
            }
            double disponible = balanceService.quantite(mvt.getProduitId(), mvt.getSiteDestinationId());
            if (disponible < aRetirer) {
                throw new StockOperationException(
                        "Contre-passement impossible pour " + mvt.getProduitCode()
                                + " : la marchandise reçue a déjà été consommée (disponible " + disponible
                                + ", à retirer " + aRetirer + "). Ajustez le stock avant de supprimer ce bon.");
            }
        }
    }

    /** Supprime les points de coût 7.6 rattachés aux mouvements effacés (le CUMP reste en l'état). */
    private void nettoyerHistoriqueCout(List<MouvementStock> mouvements) {
        List<String> references = mouvements.stream()
                .map(MouvementStock::getReference)
                .filter(Objects::nonNull)
                .toList();
        if (references.isEmpty()) {
            return;
        }
        List<HistoriquePointCout> points = historiqueCoutRepository.findByReferenceMouvementIn(references);
        if (!points.isEmpty()) {
            historiqueCoutRepository.deleteAll(points);
        }
    }

    // ─── Briques communes ───────────────────────────────────────────────────

    private void exigerSuperAdmin() {
        if (!ROLE_SUPERADMIN.equals(currentUser.currentRole())) {
            throw new StockAccesRefuseException(
                    "Suppression définitive réservée au super-administrateur");
        }
    }

    private String exigerMotif(String motif) {
        String net = motif == null ? "" : motif.trim();
        if (net.length() < MOTIF_MIN) {
            throw new IllegalArgumentException(
                    "Le motif de suppression est obligatoire (" + MOTIF_MIN + " caractères minimum)");
        }
        return net;
    }

    private LigneContrePassee ligneDe(MouvementStock mvt, double delta, String siteId) {
        return LigneContrePassee.builder()
                .produitId(mvt.getProduitId())
                .produitCode(mvt.getProduitCode())
                .delta(delta)
                .siteId(siteId)
                .build();
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
