package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.example.Pointage_Cleanic.services.stockv2.StockBalanceService.ENTREPOT;

/**
 * Génère les {@link MouvementStock} 7.3 (un par ligne) lors du passage d'un bon en EFFECTIF.
 * C'est le seul point qui touche le stock pour un bon : il réutilise le mécanisme de solde 7.3
 * ({@link StockBalanceService}).
 * <p>
 * Lot 1 / 1a (décisions D1.5, D1.6) : s'exécute <b>dans la transaction de validation du bon</b>
 * ({@link IllegalStateException} sinon). Mouvements, soldes, coût courant, historique de coût et
 * compteur de références sont validés ensemble ou pas du tout : la compensation manuelle, qui ne
 * protégeait pas d'un arrêt du processus à mi-parcours, a été retirée.
 * <p>
 * 7.6 : chaque mouvement porte le snapshot de coût ({@code coutUnitaireSnapshot}/{@code valeurMouvement}),
 * et les entrées recalculent le coût courant du produit (CUMP / DERNIER_PRIX) via
 * {@link ValorisationSupport}, dans la même transaction.
 */
@Service
@RequiredArgsConstructor
public class MouvementBonGenerator {

    private final MouvementStockRepository mouvementRepository;
    private final StockBalanceService balanceService;
    private final CompteurStockService compteurService;
    private final CurrentUserProvider currentUser;
    private final ProduitStockRepository produitRepository;
    private final ValorisationSupport valorisationSupport;

    /**
     * Bon d'entrée -> mouvements ENTREE créditant l'entrepôt, avec recalcul du coût courant.
     * <p>
     * Le site de destination du bon est purement documentaire (destination prévue de la
     * réception) : il est recopié sur le mouvement pour la traçabilité, mais le stock entre
     * toujours dans l'entrepôt unique ({@code siteId = null}), comme pour un mouvement direct
     * ou un stock initial d'import.
     */
    public void genererPourEntree(BonEntree bon) {
        exigerTransaction();
        Map<String, ProduitStock> produits = produitsParId(bon.getLignes());
        for (LigneBon ligne : bon.getLignes()) {
            // Stock AVANT l'entrée (pour le CUMP), puis crédit atomique de l'entrepôt. Dans la
            // transaction, la lecture voit les lignes précédentes du même bon.
            double stockAvant = balanceService.quantiteTotale(ligne.getProduitId());
            balanceService.appliquerDelta(ligne.getProduitId(), ENTREPOT, ligne.getQuantite());

            MouvementStock mvt = baseMouvement(bon.getReference(), bon.getId(), bon.getDate(), ligne);
            mvt.setType(TypeMouvement.ENTREE);
            mvt.setMotif(StockLibelles.motif(bon.getType()));
            mvt.setCategorieEntree(bon.getType());
            mvt.setSiteDestinationId(bon.getSiteDestinationId());
            mvt.setSiteDestinationNom(bon.getSiteDestinationNom());
            // Snapshot = prix d'achat de la ligne (coût réel des biens entrés).
            appliquerSnapshot(mvt, ligne.getPrixUnitaire());

            ProduitStock produit = produits.get(ligne.getProduitId());
            if (produit != null) {
                valorisationSupport.appliquerEntree(produit, stockAvant, ligne.getQuantite(),
                        ligne.getPrixUnitaire(), mvt.getReference(), bon.getDate());
            }

            mouvementRepository.save(mvt);
        }
    }

    /**
     * Bon de sortie -> mouvements SORTIE débitant le site source, puis l'entrepôt en repli.
     * 422 {@code STOCK_INSUFFISANT} si le stock ne couvre pas une ligne : la transaction annule tout.
     */
    public void genererPourSortie(BonSortie bon) {
        exigerTransaction();
        // Pré-vérification de TOUTES les lignes (cumulées par produit) : message clair avant toute
        // écriture. La garantie, elle, vient du débit gardé (deux validations simultanées).
        Map<String, Double> cumulParProduit = new LinkedHashMap<>();
        for (LigneBon ligne : bon.getLignes()) {
            cumulParProduit.merge(ligne.getProduitId(), ligne.getQuantite(), Double::sum);
        }
        cumulParProduit.forEach((produitId, total) ->
                balanceService.verifierDisponibiliteAvecConsolide(produitId, bon.getSiteSourceId(), total, bon.getSiteSourceNom()));

        for (LigneBon ligne : bon.getLignes()) {
            balanceService.debiterAvecRepli(ligne.getProduitId(), bon.getSiteSourceId(), ligne.getQuantite());

            MouvementStock mvt = baseMouvement(bon.getReference(), bon.getId(), bon.getDate(), ligne);
            mvt.setType(TypeMouvement.SORTIE);
            mvt.setMotif(StockLibelles.motif(bon.getType()));
            mvt.setCategorieSortie(bon.getType());
            mvt.setSiteSourceId(bon.getSiteSourceId());
            mvt.setSiteSourceNom(bon.getSiteSourceNom());
            // 7.5 : traçabilité analytique recopiée depuis le bon (don / chantier).
            mvt.setNatureDon(bon.getNatureDon());
            mvt.setBeneficiaireDon(bon.getBeneficiaireDon());
            mvt.setChantierId(bon.getChantierId());
            mvt.setChantierReference(bon.getChantierReference());
            // 7.6 : snapshot = prix figé de la ligne (prix courant à la saisie).
            appliquerSnapshot(mvt, ligne.getPrixUnitaire());

            mouvementRepository.save(mvt);
        }
    }

    private static void exigerTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Génération de mouvements hors transaction : passer par ExecuteurTransactionnel");
        }
    }

    private void appliquerSnapshot(MouvementStock mvt, long coutUnitaire) {
        mvt.setCoutUnitaireSnapshot(coutUnitaire);
        mvt.setValeurMouvement(Math.round(mvt.getQuantite() * coutUnitaire));
    }

    private Map<String, ProduitStock> produitsParId(List<LigneBon> lignes) {
        List<String> ids = lignes.stream().map(LigneBon::getProduitId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return produitRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ProduitStock::getId, Function.identity(), (a, b) -> a));
    }

    private MouvementStock baseMouvement(String bonReference, String bonId, LocalDate date, LigneBon ligne) {
        return MouvementStock.builder()
                .reference(compteurService.genererReference("MVT"))
                .produitId(ligne.getProduitId())
                .produitCode(ligne.getProduitCode())
                .produitLibelle(ligne.getProduitLibelle())
                .unite(ligne.getUnite())
                .quantite(ligne.getQuantite())
                .date(date != null ? date : LocalDate.now())
                .utilisateur(currentUser.currentUserNom())
                .origine("BON")
                .bonId(bonId)
                .bonReference(bonReference)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
