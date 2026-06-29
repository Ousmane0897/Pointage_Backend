package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComparatifCoutSitesDto;
import com.example.Pointage_Cleanic.Dto.stockv2.RapportTableauBordFinancierDto;
import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseMargesDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ValeurStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.GraviteDerive;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDerive;
import com.example.Pointage_Cleanic.entities.stockv2.HistoriquePointCout;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tableau de bord financier (Stock v2 7.6) : compose les KPIs, l'évolution de la valeur de stock
 * (12 mois), le coût par site, la répartition par catégorie et les dérives (site + produit).
 */
@Service
@RequiredArgsConstructor
public class TableauBordFinancierService {

    private static final DateTimeFormatter MOIS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ValeurStockService valeurStockService;
    private final CoutSiteService coutSiteService;
    private final MargesService margesService;
    private final AnalyseSupport analyseSupport;
    private final ValorisationSupport valorisationSupport;
    private final ProduitStockRepository produitRepository;
    private final MongoTemplate mongoTemplate;

    public RapportTableauBordFinancierDto rapport(LocalDate dateDebut, LocalDate dateFin,
                                                  String siteId, String categorieId) {
        String site = (siteId == null || siteId.isBlank()) ? null : siteId;

        ValeurStockDto valeurStock = valeurStockService.valeur(site, categorieId, null);
        ComparatifCoutSitesDto coutSites = coutSiteService.comparatif(dateDebut, dateFin, categorieId);
        SyntheseMargesDto marges = margesService.synthese(dateDebut, dateFin, categorieId);

        long valeurConsommeeMois = valeurConsommeeMoisCourant(site, categorieId);

        List<RapportTableauBordFinancierDto.Derive> derives = new ArrayList<>();
        derives.addAll(derivesSites(coutSites));
        derives.addAll(derivesProduits(categorieId));

        RapportTableauBordFinancierDto.Kpis kpis = RapportTableauBordFinancierDto.Kpis.builder()
                .valeurStock(valeurStock.getKpis().getValeurTotale())
                .valeurConsommeeMois(valeurConsommeeMois)
                .coutMoyenParSite(coutSites.getCoutMoyenParSite())
                .margeGlobale(marges.getMargeGlobaleTotale())
                .tauxMargeMoyen(marges.getTauxMargeMoyen())
                .nbDerives(derives.size())
                .build();

        List<RapportTableauBordFinancierDto.CoutParSite> coutParSite = coutSites.getLignes().stream()
                .limit(10)
                .map(l -> RapportTableauBordFinancierDto.CoutParSite.builder()
                        .siteNom(l.getSiteNom())
                        .cout(l.getCoutTotal())
                        .build())
                .toList();

        List<RapportTableauBordFinancierDto.RepartitionCategorie> repartition =
                valeurStock.getRepartitionCategorie().stream()
                        .map(r -> RapportTableauBordFinancierDto.RepartitionCategorie.builder()
                                .categorie(r.getCategorie())
                                .valeur(r.getValeur())
                                .build())
                        .toList();

        return RapportTableauBordFinancierDto.builder()
                .kpis(kpis)
                .evolutionValeur(evolutionValeur(site, categorieId))
                .coutParSite(coutParSite)
                .repartitionCategorie(repartition)
                .derives(derives)
                .build();
    }

    /** Valeur consommée sur le mois calendaire courant (sorties effectives × coût). */
    private long valeurConsommeeMoisCourant(String site, String categorieId) {
        YearMonth mois = YearMonth.now();
        AnalyseSupport.Perimetre perimetre = analyseSupport.sortiesEffectives(
                mois.atDay(1), mois.atEndOfMonth(), site, null, null, categorieId);
        Map<String, ProduitStock> produits = perimetre.produits();
        return perimetre.mouvements().stream()
                .mapToLong(m -> Math.round(m.getQuantite() * valorisationSupport.coutDe(m, produits).coutUnitaire()))
                .sum();
    }

    /** Évolution de la valeur de stock sur 12 mois (rejeu des mouvements × coût courant). */
    private List<RapportTableauBordFinancierDto.EvolutionValeur> evolutionValeur(String site, String categorieId) {
        List<ProduitStock> produits = produitRepository.findAll().stream()
                .filter(p -> categorieId == null || categorieId.isBlank() || categorieId.equals(p.getCategorieId()))
                .toList();
        YearMonth dernier = YearMonth.now();
        YearMonth courant = dernier.minusMonths(11);

        List<MouvementStock> mouvements;
        if (produits.isEmpty()) {
            mouvements = List.of();
        } else {
            List<String> ids = produits.stream().map(ProduitStock::getId).toList();
            mouvements = mongoTemplate.find(
                    new Query(Criteria.where("produitId").in(ids).and("date").lte(dernier.atEndOfMonth())),
                    MouvementStock.class);
        }
        Map<String, List<MouvementStock>> parProduit = mouvements.stream()
                .collect(Collectors.groupingBy(MouvementStock::getProduitId));

        List<RapportTableauBordFinancierDto.EvolutionValeur> evolution = new ArrayList<>();
        while (!courant.isAfter(dernier)) {
            LocalDate finMois = courant.atEndOfMonth();
            long valeurMois = 0;
            for (ProduitStock p : produits) {
                double cumul = parProduit.getOrDefault(p.getId(), List.of()).stream()
                        .filter(m -> m.getDate() != null && !m.getDate().isAfter(finMois))
                        .mapToDouble(m -> StockImpactCalculator.signedDelta(m, site))
                        .sum();
                valeurMois += Math.round(cumul * p.getPrixUnitaire());
            }
            evolution.add(RapportTableauBordFinancierDto.EvolutionValeur.builder()
                    .mois(courant.format(MOIS_FORMAT))
                    .valeur(valeurMois)
                    .build());
            courant = courant.plusMonths(1);
        }
        return evolution;
    }

    private List<RapportTableauBordFinancierDto.Derive> derivesSites(ComparatifCoutSitesDto coutSites) {
        List<RapportTableauBordFinancierDto.Derive> derives = new ArrayList<>();
        for (ComparatifCoutSitesDto.LigneSite l : coutSites.getLignes()) {
            if (l.getEcartPct() == null) {
                continue;
            }
            GraviteDerive gravite = gravite(l.getEcartPct());
            if (gravite == null) {
                continue;
            }
            derives.add(RapportTableauBordFinancierDto.Derive.builder()
                    .cible(l.getSiteNom())
                    .type(TypeDerive.SITE)
                    .valeurActuelle(l.getCoutTotal())
                    .valeurReference(l.getCoutMoyenReference() == null ? 0 : l.getCoutMoyenReference())
                    .ecartPct(l.getEcartPct())
                    .gravite(gravite)
                    .build());
        }
        return derives;
    }

    /** Dérives produit : coût courant vs coût précédent (historique). */
    private List<RapportTableauBordFinancierDto.Derive> derivesProduits(String categorieId) {
        List<ProduitStock> produits = produitRepository.findAll().stream()
                .filter(p -> categorieId == null || categorieId.isBlank() || categorieId.equals(p.getCategorieId()))
                .toList();
        if (produits.isEmpty()) {
            return List.of();
        }
        List<String> ids = produits.stream().map(ProduitStock::getId).toList();
        Map<String, List<HistoriquePointCout>> points = mongoTemplate.find(
                        new Query(Criteria.where("produitId").in(ids)), HistoriquePointCout.class).stream()
                .collect(Collectors.groupingBy(HistoriquePointCout::getProduitId));

        List<RapportTableauBordFinancierDto.Derive> derives = new ArrayList<>();
        for (ProduitStock p : produits) {
            List<HistoriquePointCout> pts = points.getOrDefault(p.getId(), List.of());
            if (pts.size() < 2) {
                continue;
            }
            pts.sort(Comparator.comparing(HistoriquePointCout::getCreatedAt).reversed());
            long coutCourant = p.getPrixUnitaire();
            long dernierCout = pts.get(1).getCout();
            if (dernierCout == 0) {
                continue;
            }
            double ecartPct = (coutCourant - dernierCout) * 100.0 / Math.abs(dernierCout);
            GraviteDerive gravite = gravite(ecartPct);
            if (gravite == null) {
                continue;
            }
            derives.add(RapportTableauBordFinancierDto.Derive.builder()
                    .cible(p.getLibelle())
                    .type(TypeDerive.PRODUIT)
                    .valeurActuelle(coutCourant)
                    .valeurReference(dernierCout)
                    .ecartPct(ecartPct)
                    .gravite(gravite)
                    .build());
        }
        return derives;
    }

    private GraviteDerive gravite(double ecartPct) {
        double abs = Math.abs(ecartPct);
        if (abs >= ValorisationSupport.SEUIL_DERIVE_CRITIQUE) {
            return GraviteDerive.CRITIQUE;
        }
        if (abs >= ValorisationSupport.SEUIL_DERIVE) {
            return GraviteDerive.ATTENTION;
        }
        return null;
    }
}
