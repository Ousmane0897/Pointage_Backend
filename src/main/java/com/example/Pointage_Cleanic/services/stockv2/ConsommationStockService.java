package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ConsommationDestinataireDto;
import com.example.Pointage_Cleanic.Dto.stockv2.EvolutionConsommationDto;
import com.example.Pointage_Cleanic.Dto.stockv2.LigneConsommationDto;
import com.example.Pointage_Cleanic.Dto.stockv2.LigneRapportDto;
import com.example.Pointage_Cleanic.Dto.stockv2.RapportConsommationDto;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeRapportConsommation;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.DestinataireBon;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Historique de consommation par destinataire + rapports agrégés (sur les mouvements EFFECTIFS). */
@Service
@RequiredArgsConstructor
public class ConsommationStockService {

    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("yyyy-MM");

    private final MongoTemplate mongoTemplate;
    private final ProduitStockRepository produitRepository;
    private final ReferentielSiteService referentielSite;

    // ---------- Consommation par destinataire (depuis les bons de sortie EFFECTIFS) ----------

    public List<ConsommationDestinataireDto> parDestinataire(String siteId, String produitId,
                                                             LocalDate dateDebut, LocalDate dateFin) {
        Query query = new Query();
        query.addCriteria(Criteria.where("statut").is(StatutBon.EFFECTIF));
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteSourceId").is(siteId));
        }
        appliquerDates(query, dateDebut, dateFin);
        List<BonSortie> bons = mongoTemplate.find(query, BonSortie.class);

        Map<String, DestAcc> accs = new LinkedHashMap<>();
        for (BonSortie bon : bons) {
            List<LigneBon> lignes = bon.getLignes() == null ? List.of() : bon.getLignes().stream()
                    .filter(l -> produitId == null || produitId.isBlank() || produitId.equals(l.getProduitId()))
                    .toList();
            if (lignes.isEmpty()) {
                continue;
            }
            DestinataireBon d = bon.getDestinataire();
            String key = destinataireId(d);
            DestAcc acc = accs.computeIfAbsent(key, k -> {
                DestAcc a = new DestAcc();
                a.id = k;
                a.nom = WorkflowStockService.nomDestinataire(d);
                a.type = d == null || d.getType() == null ? null : d.getType().name();
                return a;
            });
            acc.nbSorties++;
            String periode = bon.getDate() == null ? "" : bon.getDate().format(MOIS);
            for (LigneBon l : lignes) {
                acc.quantiteTotale += l.getQuantite();
                acc.montantTotal += l.getMontant();
                double[] ev = acc.evolution.computeIfAbsent(periode, p -> new double[2]);
                ev[0] += l.getQuantite();
                ev[1] += l.getMontant();
                LigneConsommationDto ligne = acc.lignes.computeIfAbsent(l.getProduitId(), pid ->
                        LigneConsommationDto.builder()
                                .produitId(pid).produitCode(l.getProduitCode())
                                .produitLibelle(l.getProduitLibelle()).unite(l.getUnite())
                                .quantite(0).montant(0).build());
                ligne.setQuantite(ligne.getQuantite() + l.getQuantite());
                ligne.setMontant(ligne.getMontant() + l.getMontant());
            }
        }

        List<ConsommationDestinataireDto> result = new ArrayList<>();
        for (DestAcc acc : accs.values()) {
            List<EvolutionConsommationDto> evolution = acc.evolution.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> EvolutionConsommationDto.builder()
                            .periode(e.getKey()).quantite(e.getValue()[0])
                            .montant(Math.round(e.getValue()[1])).build())
                    .toList();
            result.add(ConsommationDestinataireDto.builder()
                    .destinataireId(acc.id)
                    .destinataireNom(acc.nom)
                    .typeDestinataire(acc.type)
                    .quantiteTotale(acc.quantiteTotale)
                    .montantTotal(Math.round(acc.montantTotal))
                    .nbSorties(acc.nbSorties)
                    .evolution(evolution)
                    .lignes(new ArrayList<>(acc.lignes.values()))
                    .build());
        }
        return result;
    }

    // ---------- Rapport agrégé (depuis les mouvements SORTIE effectifs) ----------

    public RapportConsommationDto rapport(TypeRapportConsommation type, LocalDate dateDebut, LocalDate dateFin,
                                          String siteId, String produitId, String categorieId) {
        if (type == null) {
            throw new IllegalArgumentException("Le paramètre type est obligatoire");
        }
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les paramètres dateDebut et dateFin sont obligatoires");
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("type").is(TypeMouvement.SORTIE));
        query.addCriteria(Criteria.where("date").gte(dateDebut).lte(dateFin));
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteSourceId").is(siteId));
        }
        if (produitId != null && !produitId.isBlank()) {
            query.addCriteria(Criteria.where("produitId").is(produitId));
        }
        List<MouvementStock> mouvements = mongoTemplate.find(query, MouvementStock.class);

        Map<String, ProduitStock> produits = produits(mouvements);
        if (categorieId != null && !categorieId.isBlank()) {
            mouvements = mouvements.stream()
                    .filter(m -> {
                        ProduitStock p = produits.get(m.getProduitId());
                        return p != null && categorieId.equals(p.getCategorieId());
                    })
                    .toList();
        }

        Map<String, LigneAcc> groupes = new LinkedHashMap<>();
        for (MouvementStock m : mouvements) {
            String cle = cle(type, m);
            LigneAcc acc = groupes.computeIfAbsent(cle, k -> {
                LigneAcc a = new LigneAcc();
                a.cle = k;
                a.libelle = libelle(type, m);
                return a;
            });
            long prix = produits.containsKey(m.getProduitId()) ? produits.get(m.getProduitId()).getPrixUnitaire() : 0L;
            acc.quantite += m.getQuantite();
            acc.montant += m.getQuantite() * prix;
            acc.nbMouvements++;
        }

        List<LigneRapportDto> lignes = groupes.values().stream()
                .map(a -> LigneRapportDto.builder()
                        .cle(a.cle).libelle(a.libelle)
                        .quantite(a.quantite).montant(Math.round(a.montant))
                        .nbMouvements(a.nbMouvements).build())
                .toList();

        double quantiteTotale = lignes.stream().mapToDouble(LigneRapportDto::getQuantite).sum();
        long montantTotal = lignes.stream().mapToLong(LigneRapportDto::getMontant).sum();
        long nbMouvementsTotal = lignes.stream().mapToLong(LigneRapportDto::getNbMouvements).sum();
        double coutMoyen = nbMouvementsTotal == 0 ? 0 : (double) montantTotal / nbMouvementsTotal;

        return RapportConsommationDto.builder()
                .type(type)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .siteNom(siteId == null || siteId.isBlank() ? null : referentielSite.nomDuSite(siteId))
                .produitLibelle(produitId == null || produitId.isBlank() ? null
                        : produits.values().stream().filter(p -> p.getId().equals(produitId))
                        .map(ProduitStock::getLibelle).findFirst().orElse(null))
                .lignes(lignes)
                .quantiteTotale(quantiteTotale)
                .montantTotal(montantTotal)
                .nbMouvementsTotal(nbMouvementsTotal)
                .coutMoyenParMouvement(coutMoyen)
                .build();
    }

    private String cle(TypeRapportConsommation type, MouvementStock m) {
        return switch (type) {
            case PAR_SITE -> m.getSiteSourceId() == null ? "—" : m.getSiteSourceId();
            case PAR_PRODUIT -> m.getProduitId();
            case PAR_PERIODE -> m.getDate() == null ? "—" : m.getDate().format(MOIS);
        };
    }

    private String libelle(TypeRapportConsommation type, MouvementStock m) {
        return switch (type) {
            case PAR_SITE -> m.getSiteSourceNom() != null ? m.getSiteSourceNom() : referentielSite.nomDuSite(m.getSiteSourceId());
            case PAR_PRODUIT -> m.getProduitLibelle();
            case PAR_PERIODE -> m.getDate() == null ? "—" : m.getDate().format(MOIS);
        };
    }

    private Map<String, ProduitStock> produits(List<MouvementStock> mouvements) {
        Set<String> ids = mouvements.stream().map(MouvementStock::getProduitId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mongoTemplate.find(new Query(Criteria.where("_id").in(ids)), ProduitStock.class).stream()
                .collect(Collectors.toMap(ProduitStock::getId, p -> p));
    }

    private void appliquerDates(Query query, LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut != null && dateFin != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut).lte(dateFin));
        } else if (dateDebut != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut));
        } else if (dateFin != null) {
            query.addCriteria(Criteria.where("date").lte(dateFin));
        }
    }

    private String destinataireId(DestinataireBon d) {
        if (d == null || d.getType() == null) {
            return "—";
        }
        return switch (d.getType()) {
            case SITE -> d.getSiteId();
            case AGENT -> d.getAgentId();
            case CLIENT -> d.getClientNom();
        };
    }

    private static class DestAcc {
        String id;
        String nom;
        String type;
        double quantiteTotale;
        double montantTotal;
        long nbSorties;
        Map<String, double[]> evolution = new LinkedHashMap<>();
        Map<String, LigneConsommationDto> lignes = new LinkedHashMap<>();
    }

    private static class LigneAcc {
        String cle;
        String libelle;
        double quantite;
        double montant;
        long nbMouvements;
    }
}
