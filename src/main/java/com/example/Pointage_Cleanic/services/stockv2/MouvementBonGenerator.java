package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.services.stockv2.ValorisationSupport.RecalcResult;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Génère les {@link MouvementStock} 7.3 (un par ligne) lors du passage d'un bon en EFFECTIF.
 * C'est le seul point qui touche le stock pour un bon : il réutilise le mécanisme de solde 7.3
 * ({@link StockBalanceService}) et applique une compensation manuelle (pas de transaction Mongo)
 * pour garantir l'atomicité all-or-nothing.
 * <p>
 * 7.6 : chaque mouvement porte le snapshot de coût ({@code coutUnitaireSnapshot}/{@code valeurMouvement}),
 * et les entrées recalculent le coût courant du produit (CUMP / DERNIER_PRIX) via
 * {@link ValorisationSupport} — recalcul également compensé en cas d'échec à mi-parcours.
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

    /** Bon d'entrée -> mouvements ENTREE créditant le site destination, avec recalcul du coût courant. */
    public void genererPourEntree(BonEntree bon) {
        Map<String, ProduitStock> produits = produitsParId(bon.getLignes());
        List<MouvementStock> sauvegardes = new ArrayList<>();
        List<RecalcResult> recalculs = new ArrayList<>();
        List<LigneBon> deltasAppliques = new ArrayList<>();
        try {
            for (LigneBon ligne : bon.getLignes()) {
                // Stock AVANT l'entrée (pour le CUMP), puis application du delta.
                double stockAvant = balanceService.quantiteTotale(ligne.getProduitId());
                balanceService.appliquerDelta(ligne.getProduitId(), bon.getSiteDestinationId(), ligne.getQuantite());
                deltasAppliques.add(ligne);

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
                    recalculs.add(valorisationSupport.appliquerEntree(produit, stockAvant, ligne.getQuantite(),
                            ligne.getPrixUnitaire(), mvt.getReference(), bon.getDate()));
                }

                sauvegardes.add(mouvementRepository.save(mvt));
            }
        } catch (RuntimeException ex) {
            // Compensation : mouvements, recalculs de coût, puis deltas de solde.
            sauvegardes.forEach(m -> mouvementRepository.deleteById(m.getId()));
            recalculs.forEach(valorisationSupport::compenserEntree);
            Collections.reverse(deltasAppliques);
            deltasAppliques.forEach(ligne ->
                    balanceService.appliquerDelta(ligne.getProduitId(), bon.getSiteDestinationId(), -ligne.getQuantite()));
            throw ex;
        }
    }

    /** Bon de sortie -> mouvements SORTIE débitant le site source (422 si stock insuffisant). */
    public void genererPourSortie(BonSortie bon) {
        // Pré-vérification de TOUTES les lignes (cumulées par produit) AVANT toute écriture.
        Map<String, Double> cumulParProduit = new LinkedHashMap<>();
        for (LigneBon ligne : bon.getLignes()) {
            cumulParProduit.merge(ligne.getProduitId(), ligne.getQuantite(), Double::sum);
        }
        cumulParProduit.forEach((produitId, total) ->
                balanceService.verifierDisponibilite(produitId, bon.getSiteSourceId(), total, bon.getSiteSourceNom()));

        appliquer(bon.getLignes(), (ligne) -> {
            balanceService.appliquerDelta(ligne.getProduitId(), bon.getSiteSourceId(), -ligne.getQuantite());
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
            return mvt;
        }, (ligne) -> balanceService.appliquerDelta(ligne.getProduitId(), bon.getSiteSourceId(), ligne.getQuantite()));
    }

    private interface LigneToMouvement {
        MouvementStock apply(LigneBon ligne);
    }

    private interface LigneCompensation {
        void apply(LigneBon ligne);
    }

    private void appliquer(List<LigneBon> lignes, LigneToMouvement builder, LigneCompensation inverse) {
        List<MouvementStock> sauvegardes = new ArrayList<>();
        List<LigneBon> deltasAppliques = new ArrayList<>();
        try {
            for (LigneBon ligne : lignes) {
                MouvementStock mvt = builder.apply(ligne);
                deltasAppliques.add(ligne);
                sauvegardes.add(mouvementRepository.save(mvt));
            }
        } catch (RuntimeException ex) {
            // Compensation : supprimer les mouvements créés puis annuler les deltas de solde.
            sauvegardes.forEach(m -> mouvementRepository.deleteById(m.getId()));
            Collections.reverse(deltasAppliques);
            deltasAppliques.forEach(inverse::apply);
            throw ex;
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
