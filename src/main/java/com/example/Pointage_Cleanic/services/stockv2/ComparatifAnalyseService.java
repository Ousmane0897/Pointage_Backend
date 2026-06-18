package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MatriceComparatifDto;
import com.example.Pointage_Cleanic.Enum.stockv2.AxeComparatif;
import com.example.Pointage_Cleanic.Enum.stockv2.SensEvolution;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Comparatif mensuel par site ou produit (7.5 — /analyse/comparatif). Valeurs = montant FCFA. */
@Service
@RequiredArgsConstructor
public class ComparatifAnalyseService {

    private final AnalyseSupport support;

    public MatriceComparatifDto comparatif(AxeComparatif axe, String dateDebut, String dateFin, String siteId,
                                           String categorieId, TypeSortie typeSortie, double seuilPct) {
        if (axe == null) {
            throw new IllegalArgumentException("Le paramètre axe (SITE|PRODUIT) est obligatoire");
        }
        YearMonth debut = parseMois(dateDebut, "dateDebut");
        YearMonth fin = parseMois(dateFin, "dateFin");
        if (fin.isBefore(debut)) {
            throw new IllegalArgumentException("dateFin ne peut pas précéder dateDebut");
        }

        List<String> mois = new ArrayList<>();
        for (YearMonth ym = debut; !ym.isAfter(fin); ym = ym.plusMonths(1)) {
            mois.add(ym.format(AnalyseSupport.MOIS));
        }

        AnalyseSupport.Perimetre perimetre = support.sortiesEffectives(
                debut.atDay(1), fin.atEndOfMonth(), siteId, null, typeSortie, categorieId);

        // cle -> (libelle, mois -> [montant, quantite])
        Map<String, String> libelles = new LinkedHashMap<>();
        Map<String, Map<String, double[]>> data = new LinkedHashMap<>();
        for (MouvementStock m : perimetre.mouvements()) {
            if (m.getDate() == null) {
                continue;
            }
            String cle = axe == AxeComparatif.SITE ? m.getSiteSourceId() : m.getProduitId();
            if (cle == null) {
                cle = "—";
            }
            libelles.putIfAbsent(cle, axe == AxeComparatif.SITE
                    ? (m.getSiteSourceNom() != null ? m.getSiteSourceNom() : cle)
                    : (m.getProduitLibelle() != null ? m.getProduitLibelle() : cle));
            String moisCle = YearMonth.from(m.getDate()).format(AnalyseSupport.MOIS);
            double[] cell = data.computeIfAbsent(cle, k -> new LinkedHashMap<>())
                    .computeIfAbsent(moisCle, k -> new double[2]);
            cell[0] += support.montant(m, perimetre.produits());
            cell[1] += m.getQuantite();
        }

        List<MatriceComparatifDto.LigneComparatif> lignes = new ArrayList<>();
        List<MatriceComparatifDto.Serie> series = new ArrayList<>();
        long[] totauxParMois = new long[mois.size()];
        long totalGeneral = 0;
        int nbAlertes = 0;

        for (Map.Entry<String, String> entry : libelles.entrySet()) {
            String cle = entry.getKey();
            Map<String, double[]> parMois = data.getOrDefault(cle, Map.of());
            List<MatriceComparatifDto.Cellule> cellules = new ArrayList<>();
            List<Long> serieData = new ArrayList<>();
            long total = 0;
            Long precedente = null;
            for (int i = 0; i < mois.size(); i++) {
                String m = mois.get(i);
                double[] cell = parMois.get(m);
                long valeur = cell == null ? 0 : Math.round(cell[0]);
                Double quantite = cell == null ? null : cell[1];
                Eval eval = evaluer(valeur, precedente, seuilPct);
                if (eval.sens() == SensEvolution.ALERTE) {
                    nbAlertes++;
                }
                cellules.add(MatriceComparatifDto.Cellule.builder()
                        .mois(m).valeur(valeur).quantite(quantite)
                        .evolutionPct(eval.pct()).sens(eval.sens()).build());
                serieData.add(valeur);
                total += valeur;
                totauxParMois[i] += valeur;
                precedente = valeur;
            }
            totalGeneral += total;

            long premier = cellules.isEmpty() ? 0 : cellules.get(0).getValeur();
            long dernier = cellules.isEmpty() ? 0 : cellules.get(cellules.size() - 1).getValeur();
            Eval global = evaluer(dernier, mois.size() <= 1 ? null : premier, seuilPct);

            lignes.add(MatriceComparatifDto.LigneComparatif.builder()
                    .cleId(cle)
                    .libelle(entry.getValue())
                    .cellules(cellules)
                    .total(total)
                    .evolutionGlobalePct(global.pct())
                    .sensGlobal(global.sens())
                    .build());
            series.add(MatriceComparatifDto.Serie.builder().label(entry.getValue()).data(serieData).build());
        }

        lignes.sort(Comparator.comparingLong(MatriceComparatifDto.LigneComparatif::getTotal).reversed());

        List<Long> totauxList = new ArrayList<>();
        for (long t : totauxParMois) {
            totauxList.add(t);
        }

        return MatriceComparatifDto.builder()
                .axe(axe)
                .mois(mois)
                .lignes(lignes)
                .series(series)
                .totauxParMois(totauxList)
                .totalGeneral(totalGeneral)
                .nbAlertes(nbAlertes)
                .build();
    }

    private record Eval(Double pct, SensEvolution sens) {
    }

    /** Barème 7.5 : 1ère colonne (precedente null) → STABLE/null ; sinon écart % vs précédent. */
    private Eval evaluer(long valeur, Long precedente, double seuilPct) {
        if (precedente == null) {
            return new Eval(null, SensEvolution.STABLE);
        }
        if (precedente == 0) {
            return new Eval(null, valeur == 0 ? SensEvolution.STABLE : SensEvolution.HAUSSE);
        }
        double pct = (valeur - precedente) * 100.0 / precedente;
        double arrondi = Math.round(pct * 100.0) / 100.0;
        SensEvolution sens;
        if (pct > seuilPct) {
            sens = SensEvolution.ALERTE;
        } else if (pct > 0) {
            sens = SensEvolution.HAUSSE;
        } else if (pct < 0) {
            sens = SensEvolution.BAISSE;
        } else {
            sens = SensEvolution.STABLE;
        }
        return new Eval(arrondi, sens);
    }

    private static YearMonth parseMois(String valeur, String champ) {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le paramètre '" + champ + "' (yyyy-MM) est obligatoire");
        }
        try {
            return YearMonth.parse(valeur);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Paramètre '" + champ + "' invalide (attendu yyyy-MM) : " + valeur);
        }
    }
}
