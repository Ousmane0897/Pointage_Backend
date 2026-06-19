package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseMargesDto;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Marges des produits vendus (Stock v2 7.6). Quantité vendue = sorties effectives
 * {@code categorieSortie=VENTE_PRODUIT}. N'inclut que les produits ayant un prix de vente défini
 * ET une quantité vendue > 0.
 */
@Service
@RequiredArgsConstructor
public class MargesService {

    private final AnalyseSupport analyseSupport;

    public SyntheseMargesDto synthese(LocalDate dateDebut, LocalDate dateFin, String categorieId) {
        AnalyseSupport.Perimetre perimetre = analyseSupport.sortiesEffectives(
                dateDebut, dateFin, null, null, TypeSortie.VENTE_PRODUIT, categorieId);
        Map<String, ProduitStock> produits = perimetre.produits();
        Map<String, String> categories = analyseSupport.libellesCategories(produits);

        Map<String, Double> quantiteParProduit = new LinkedHashMap<>();
        for (MouvementStock m : perimetre.mouvements()) {
            quantiteParProduit.merge(m.getProduitId(), m.getQuantite(), Double::sum);
        }

        List<SyntheseMargesDto.LigneMarge> lignes = new ArrayList<>();
        long margeGlobaleTotale = 0;
        long chiffreAffaires = 0;
        long coutTotal = 0;
        int nbNonRentables = 0;

        for (Map.Entry<String, Double> e : quantiteParProduit.entrySet()) {
            ProduitStock p = produits.get(e.getKey());
            double quantiteVendue = e.getValue();
            if (p == null || p.getPrixVente() == null || quantiteVendue <= 0) {
                continue;
            }
            long prixVente = p.getPrixVente();
            long coutRevient = p.getPrixUnitaire();
            long margeUnitaire = prixVente - coutRevient;
            double tauxMarge = prixVente == 0 ? 0.0 : margeUnitaire * 100.0 / prixVente;
            long margeGlobale = Math.round(margeUnitaire * quantiteVendue);
            boolean rentable = margeUnitaire >= 0 && tauxMarge >= ValorisationSupport.MARGE_MINI;

            margeGlobaleTotale += margeGlobale;
            chiffreAffaires += Math.round(prixVente * quantiteVendue);
            coutTotal += Math.round(coutRevient * quantiteVendue);
            if (!rentable) {
                nbNonRentables++;
            }

            lignes.add(SyntheseMargesDto.LigneMarge.builder()
                    .produitId(p.getId())
                    .produitCode(p.getCode())
                    .produitLibelle(p.getLibelle())
                    .typeProduit(p.getTypeProduit())
                    .categorieLibelle(p.getCategorieId() == null ? null : categories.get(p.getCategorieId()))
                    .prixVente(prixVente)
                    .coutRevient(coutRevient)
                    .margeUnitaire(margeUnitaire)
                    .tauxMarge(tauxMarge)
                    .quantiteVendue(quantiteVendue)
                    .margeGlobale(margeGlobale)
                    .rentable(rentable)
                    .build());
        }

        double tauxMargeMoyen = chiffreAffaires == 0 ? 0.0 : margeGlobaleTotale * 100.0 / chiffreAffaires;

        return SyntheseMargesDto.builder()
                .lignes(lignes)
                .margeGlobaleTotale(margeGlobaleTotale)
                .chiffreAffaires(chiffreAffaires)
                .coutTotal(coutTotal)
                .tauxMargeMoyen(tauxMargeMoyen)
                .nbProduitsNonRentables(nbNonRentables)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .build();
    }
}
