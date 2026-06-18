package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
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

/**
 * Génère les {@link MouvementStock} 7.3 (un par ligne) lors du passage d'un bon en EFFECTIF.
 * C'est le seul point qui touche le stock pour un bon : il réutilise le mécanisme de solde 7.3
 * ({@link StockBalanceService}) et applique une compensation manuelle (pas de transaction Mongo)
 * pour garantir l'atomicité all-or-nothing.
 */
@Service
@RequiredArgsConstructor
public class MouvementBonGenerator {

    private final MouvementStockRepository mouvementRepository;
    private final StockBalanceService balanceService;
    private final CompteurStockService compteurService;
    private final CurrentUserProvider currentUser;

    /** Bon d'entrée -> mouvements ENTREE créditant le site destination. */
    public void genererPourEntree(BonEntree bon) {
        appliquer(bon.getLignes(), (ligne) -> {
            balanceService.appliquerDelta(ligne.getProduitId(), bon.getSiteDestinationId(), ligne.getQuantite());
            MouvementStock mvt = baseMouvement(bon.getReference(), bon.getId(), bon.getDate(), ligne);
            mvt.setType(TypeMouvement.ENTREE);
            mvt.setMotif(StockLibelles.motif(bon.getType()));
            mvt.setCategorieEntree(bon.getType());
            mvt.setSiteDestinationId(bon.getSiteDestinationId());
            mvt.setSiteDestinationNom(bon.getSiteDestinationNom());
            return mvt;
        }, (ligne) -> balanceService.appliquerDelta(ligne.getProduitId(), bon.getSiteDestinationId(), -ligne.getQuantite()));
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
