package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComparatifCoutSitesDto;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Coût de consommation par site (Stock v2 7.6) : agrège les sorties effectives par site source,
 * classe par coût décroissant et détecte la surconsommation vs la moyenne inter-sites.
 */
@Service
@RequiredArgsConstructor
public class CoutSiteService {

    private final AnalyseSupport analyseSupport;
    private final ValorisationSupport valorisationSupport;

    public ComparatifCoutSitesDto comparatif(LocalDate dateDebut, LocalDate dateFin, String categorieId) {
        AnalyseSupport.Perimetre perimetre =
                analyseSupport.sortiesEffectives(dateDebut, dateFin, null, null, null, categorieId);
        Map<String, ProduitStock> produits = perimetre.produits();

        Map<String, Agg> parSite = new LinkedHashMap<>();
        for (MouvementStock m : perimetre.mouvements()) {
            String siteId = m.getSiteSourceId();
            Agg agg = parSite.computeIfAbsent(siteId, k -> new Agg(m.getSiteSourceNom()));
            long montant = Math.round(m.getQuantite() * valorisationSupport.coutDe(m, produits).coutUnitaire());
            agg.coutTotal += montant;
            agg.nbSorties++;
            agg.quantiteTotale += m.getQuantite();
        }

        long coutTotalGlobal = parSite.values().stream().mapToLong(a -> a.coutTotal).sum();
        int nbSites = parSite.size();
        long coutMoyenParSite = nbSites == 0 ? 0 : Math.round((double) coutTotalGlobal / nbSites);

        List<ComparatifCoutSitesDto.LigneSite> lignes = new ArrayList<>();
        int nbSurconso = 0;
        for (Map.Entry<String, Agg> e : parSite.entrySet()) {
            Agg a = e.getValue();
            Double ecartPct = coutMoyenParSite == 0 ? null
                    : (a.coutTotal - coutMoyenParSite) * 100.0 / coutMoyenParSite;
            boolean surconso = ecartPct != null && ecartPct > ValorisationSupport.SEUIL_DERIVE;
            if (surconso) {
                nbSurconso++;
            }
            lignes.add(ComparatifCoutSitesDto.LigneSite.builder()
                    .siteId(e.getKey())
                    .siteNom(a.siteNom)
                    .coutTotal(a.coutTotal)
                    .nbSorties(a.nbSorties)
                    .quantiteTotale(a.quantiteTotale)
                    .pourcentage(coutTotalGlobal == 0 ? 0.0 : a.coutTotal * 100.0 / coutTotalGlobal)
                    .coutMoyenReference(nbSites == 0 ? null : coutMoyenParSite)
                    .ecartPct(ecartPct)
                    .surconsommation(surconso)
                    .build());
        }
        lignes.sort(Comparator.comparingLong(ComparatifCoutSitesDto.LigneSite::getCoutTotal).reversed());

        return ComparatifCoutSitesDto.builder()
                .lignes(lignes)
                .coutTotalGlobal(coutTotalGlobal)
                .coutMoyenParSite(coutMoyenParSite)
                .nbSitesSurconsommation(nbSurconso)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .build();
    }

    private static final class Agg {
        private final String siteNom;
        private long coutTotal;
        private long nbSorties;
        private double quantiteTotale;

        private Agg(String siteNom) {
            this.siteNom = siteNom;
        }
    }
}
