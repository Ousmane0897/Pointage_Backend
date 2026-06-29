package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ResultatCroiseDto;
import com.example.Pointage_Cleanic.Enum.stockv2.AxeAnalyse;
import com.example.Pointage_Cleanic.Enum.stockv2.MesureCroise;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Tableau croisé dynamique (7.5 — /analyse/croise). Pivot 1D ou 2D selon axeColonnes. */
@Service
@RequiredArgsConstructor
public class FiltreCroiseService {

    private final AnalyseSupport support;

    public ResultatCroiseDto croise(AxeAnalyse axeLignes, AxeAnalyse axeColonnes, MesureCroise mesure,
                                    LocalDate dateDebut, LocalDate dateFin, String siteId, String produitId,
                                    String categorieId, TypeSortie typeSortie) {
        if (axeLignes == null) {
            throw new IllegalArgumentException("Le paramètre axeLignes est obligatoire");
        }
        if (mesure == null) {
            throw new IllegalArgumentException("Le paramètre mesure (MONTANT|QUANTITE) est obligatoire");
        }
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les paramètres dateDebut et dateFin sont obligatoires");
        }

        AnalyseSupport.Perimetre perimetre = support.sortiesEffectives(
                dateDebut, dateFin, siteId, produitId, typeSortie, categorieId);
        Map<String, String> categories = support.libellesCategories(perimetre.produits());

        boolean bidim = axeColonnes != null;
        // lignes : cle -> (colonne -> valeur)
        Map<String, Map<String, Double>> matrice = lignesTriees(axeLignes);
        Map<String, Double> colonnes = colonnesTriees(axeColonnes);

        for (MouvementStock m : perimetre.mouvements()) {
            String ligne = modalite(axeLignes, m, perimetre.produits(), categories);
            double valeur = mesure == MesureCroise.MONTANT
                    ? support.montant(m, perimetre.produits()) : m.getQuantite();
            Map<String, Double> parColonne = matrice.computeIfAbsent(ligne, k -> new LinkedHashMap<>());
            if (bidim) {
                String col = modalite(axeColonnes, m, perimetre.produits(), categories);
                colonnes.merge(col, 0.0, Double::sum); // enregistre la colonne
                parColonne.merge(col, valeur, Double::sum);
            } else {
                parColonne.merge("__total__", valeur, Double::sum);
            }
        }

        List<String> entetes = bidim ? new ArrayList<>(colonnes.keySet()) : List.of();
        List<ResultatCroiseDto.LigneCroise> lignes = new ArrayList<>();
        double[] totauxColonnes = new double[entetes.size()];
        double totalGeneral = 0;

        for (Map.Entry<String, Map<String, Double>> e : matrice.entrySet()) {
            Map<String, Double> parColonne = e.getValue();
            List<Double> valeurs = new ArrayList<>();
            double total = 0;
            if (bidim) {
                for (int i = 0; i < entetes.size(); i++) {
                    double v = arrondi(mesure, parColonne.getOrDefault(entetes.get(i), 0.0));
                    valeurs.add(v);
                    totauxColonnes[i] += v;
                    total += v;
                }
            } else {
                total = arrondi(mesure, parColonne.getOrDefault("__total__", 0.0));
            }
            totalGeneral += total;
            lignes.add(ResultatCroiseDto.LigneCroise.builder()
                    .libelle(e.getKey()).valeurs(valeurs).total(total).build());
        }

        List<Double> totauxColonnesList = new ArrayList<>();
        for (double t : totauxColonnes) {
            totauxColonnesList.add(t);
        }

        return ResultatCroiseDto.builder()
                .mesure(mesure)
                .entetesColonnes(entetes)
                .lignes(lignes)
                .totauxColonnes(totauxColonnesList)
                .totalGeneral(totalGeneral)
                .build();
    }

    /** Modalité (libellé) d'un mouvement pour un axe donné. */
    private String modalite(AxeAnalyse axe, MouvementStock m, Map<String, ProduitStock> produits,
                            Map<String, String> categories) {
        return switch (axe) {
            case PRODUIT -> m.getProduitLibelle() != null ? m.getProduitLibelle() : m.getProduitId();
            case CATEGORIE -> {
                ProduitStock p = produits.get(m.getProduitId());
                String catId = p == null ? null : p.getCategorieId();
                yield catId != null ? categories.getOrDefault(catId, "Sans catégorie") : "Sans catégorie";
            }
            case SITE -> m.getSiteSourceNom() != null ? m.getSiteSourceNom()
                    : (m.getSiteSourceId() != null ? m.getSiteSourceId() : "—");
            case TYPE_SORTIE -> m.getCategorieSortie() != null ? m.getCategorieSortie().name() : "—";
            case NATURE_DON -> m.getNatureDon() != null ? m.getNatureDon().name() : "—";
            case MOIS -> m.getDate() != null ? YearMonth.from(m.getDate()).format(AnalyseSupport.MOIS) : "—";
        };
    }

    /** MOIS trié chronologiquement (TreeMap), sinon ordre d'apparition (LinkedHashMap). */
    private Map<String, Map<String, Double>> lignesTriees(AxeAnalyse axe) {
        return axe == AxeAnalyse.MOIS ? new TreeMap<>() : new LinkedHashMap<>();
    }

    private Map<String, Double> colonnesTriees(AxeAnalyse axe) {
        return axe == AxeAnalyse.MOIS ? new TreeMap<>() : new LinkedHashMap<>();
    }

    private double arrondi(MesureCroise mesure, double v) {
        return mesure == MesureCroise.MONTANT ? Math.round(v) : v;
    }
}
