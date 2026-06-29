package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseConsoMensuelleDto;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Vue mensuelle de consommation (7.5 — /analyse/mensuel). */
@Service
@RequiredArgsConstructor
public class AnalyseMensuelleService {

    private final AnalyseSupport support;

    public SyntheseConsoMensuelleDto synthese(String mois, String moisFin, String siteId, String categorieId) {
        YearMonth debut = parseMois(mois, "mois");
        YearMonth fin = (moisFin == null || moisFin.isBlank()) ? debut : parseMois(moisFin, "moisFin");
        if (fin.isBefore(debut)) {
            throw new IllegalArgumentException("'moisFin' ne peut pas précéder 'mois'");
        }
        long nbMois = debut.until(fin, java.time.temporal.ChronoUnit.MONTHS) + 1;
        YearMonth debutPrec = debut.minusMonths(nbMois);
        YearMonth finPrec = debut.minusMonths(1);

        AnalyseSupport.Perimetre courant = support.sortiesEffectives(
                debut.atDay(1), fin.atEndOfMonth(), siteId, null, null, categorieId);
        AnalyseSupport.Perimetre precedent = support.sortiesEffectives(
                debutPrec.atDay(1), finPrec.atEndOfMonth(), siteId, null, null, categorieId);

        Map<String, double[]> parProduit = aggParProduit(courant);     // [quantite, cout]
        Map<String, double[]> parProduitPrec = aggParProduit(precedent);
        Map<String, ProduitStock> produits = courant.produits();
        Map<String, String> categories = support.libellesCategories(produits);

        // Lignes par produit
        List<SyntheseConsoMensuelleDto.LigneMensuelle> lignes = new ArrayList<>();
        for (Map.Entry<String, double[]> e : parProduit.entrySet()) {
            String pid = e.getKey();
            ProduitStock p = produits.get(pid);
            double quantite = e.getValue()[0];
            long cout = Math.round(e.getValue()[1]);
            double[] prec = parProduitPrec.get(pid);
            Double quantitePrec = prec == null ? null : prec[0];
            Long coutPrec = prec == null ? null : Math.round(prec[1]);
            Double evolutionPct = (coutPrec != null && coutPrec > 0)
                    ? arrondiPct((cout - coutPrec) * 100.0 / coutPrec) : null;
            lignes.add(SyntheseConsoMensuelleDto.LigneMensuelle.builder()
                    .produitId(pid)
                    .produitCode(p == null ? null : p.getCode())
                    .produitLibelle(p == null ? null : p.getLibelle())
                    .unite(p == null ? null : p.getUnite())
                    .quantite(quantite)
                    .cout(cout)
                    .quantitePrecedente(quantitePrec)
                    .coutPrecedent(coutPrec)
                    .evolutionPct(evolutionPct)
                    .build());
        }
        lignes.sort(Comparator.comparingLong(SyntheseConsoMensuelleDto.LigneMensuelle::getCout).reversed());

        long coutTotal = lignes.stream().mapToLong(SyntheseConsoMensuelleDto.LigneMensuelle::getCout).sum();
        double quantiteTotale = lignes.stream().mapToDouble(SyntheseConsoMensuelleDto.LigneMensuelle::getQuantite).sum();
        long coutTotalPrec = parProduitPrec.values().stream().mapToLong(v -> Math.round(v[1])).sum();
        Double evolutionCoutPct = coutTotalPrec > 0
                ? arrondiPct((coutTotal - coutTotalPrec) * 100.0 / coutTotalPrec) : null;

        SyntheseConsoMensuelleDto.Kpis kpis = SyntheseConsoMensuelleDto.Kpis.builder()
                .coutTotal(coutTotal)
                .quantiteTotale(quantiteTotale)
                .nbProduits(lignes.size())
                .nbMouvements(courant.mouvements().size())
                .evolutionCoutPct(evolutionCoutPct)
                .build();

        return SyntheseConsoMensuelleDto.builder()
                .kpis(kpis)
                .lignes(lignes)
                .evolution(evolution(courant, debut, fin))
                .topProduits(top(lignes))
                .repartitionCategorie(repartitionCategorie(lignes, produits, categories))
                .build();
    }

    private Map<String, double[]> aggParProduit(AnalyseSupport.Perimetre perimetre) {
        Map<String, double[]> agg = new LinkedHashMap<>();
        for (MouvementStock m : perimetre.mouvements()) {
            double[] a = agg.computeIfAbsent(m.getProduitId(), k -> new double[2]);
            a[0] += m.getQuantite();
            a[1] += support.montant(m, perimetre.produits());
        }
        return agg;
    }

    private List<SyntheseConsoMensuelleDto.PointEvolution> evolution(AnalyseSupport.Perimetre perimetre,
                                                                     YearMonth debut, YearMonth fin) {
        Map<String, double[]> parMois = new LinkedHashMap<>();
        for (YearMonth ym = debut; !ym.isAfter(fin); ym = ym.plusMonths(1)) {
            parMois.put(ym.format(AnalyseSupport.MOIS), new double[2]);
        }
        for (MouvementStock m : perimetre.mouvements()) {
            if (m.getDate() == null) {
                continue;
            }
            double[] a = parMois.get(YearMonth.from(m.getDate()).format(AnalyseSupport.MOIS));
            if (a != null) {
                a[0] += support.montant(m, perimetre.produits());
                a[1] += m.getQuantite();
            }
        }
        List<SyntheseConsoMensuelleDto.PointEvolution> points = new ArrayList<>();
        parMois.forEach((m, a) -> points.add(SyntheseConsoMensuelleDto.PointEvolution.builder()
                .mois(m).cout(Math.round(a[0])).quantite(a[1]).build()));
        return points;
    }

    private List<SyntheseConsoMensuelleDto.RepartitionItem> top(List<SyntheseConsoMensuelleDto.LigneMensuelle> lignes) {
        return lignes.stream()
                .sorted(Comparator.comparingLong(SyntheseConsoMensuelleDto.LigneMensuelle::getCout).reversed())
                .limit(10)
                .map(l -> SyntheseConsoMensuelleDto.RepartitionItem.builder()
                        .libelle(l.getProduitLibelle())
                        .montant(l.getCout())
                        .quantite(l.getQuantite())
                        .build())
                .toList();
    }

    private List<SyntheseConsoMensuelleDto.RepartitionItem> repartitionCategorie(
            List<SyntheseConsoMensuelleDto.LigneMensuelle> lignes,
            Map<String, ProduitStock> produits, Map<String, String> categories) {
        Map<String, double[]> parCat = new LinkedHashMap<>(); // [montant, quantite]
        for (SyntheseConsoMensuelleDto.LigneMensuelle l : lignes) {
            ProduitStock p = produits.get(l.getProduitId());
            String catId = p == null ? null : p.getCategorieId();
            String libelle = catId != null ? categories.getOrDefault(catId, "Sans catégorie") : "Sans catégorie";
            double[] a = parCat.computeIfAbsent(libelle, k -> new double[2]);
            a[0] += l.getCout();
            a[1] += l.getQuantite();
        }
        List<SyntheseConsoMensuelleDto.RepartitionItem> items = new ArrayList<>();
        parCat.forEach((libelle, a) -> items.add(SyntheseConsoMensuelleDto.RepartitionItem.builder()
                .libelle(libelle).montant(Math.round(a[0])).quantite(a[1]).build()));
        items.sort(Comparator.comparingLong(SyntheseConsoMensuelleDto.RepartitionItem::getMontant).reversed());
        return items;
    }

    private static double arrondiPct(double pct) {
        return Math.round(pct * 100.0) / 100.0;
    }

    private static YearMonth parseMois(String valeur, String champ) {
        try {
            return YearMonth.parse(valeur);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Paramètre '" + champ + "' invalide (attendu yyyy-MM) : " + valeur);
        }
    }
}
