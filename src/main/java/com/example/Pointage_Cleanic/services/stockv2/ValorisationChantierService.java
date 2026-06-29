package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ChantierValoriseDto;
import com.example.Pointage_Cleanic.Dto.stockv2.CoutRevientChantierDto;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutChantier;
import com.example.Pointage_Cleanic.entities.stockv2.Chantier;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Coût de revient par chantier (Stock v2 7.6). Lecture seule sur l'entité {@link Chantier} (7.5) :
 * valorise les sorties rattachées au coût de revient (snapshot ou reconstitué) et compare aux
 * chantiers similaires (même site).
 */
@Service
@RequiredArgsConstructor
public class ValorisationChantierService {

    private final MouvementStockRepository mouvementRepository;
    private final AnalyseSupport analyseSupport;
    private final ValorisationSupport valorisationSupport;
    private final MongoTemplate mongoTemplate;

    public PageResponse<ChantierValoriseDto> list(int page, int size, String q, StatutChantier statut,
                                                  String siteId, LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateDebut").descending());
        Query query = new Query().with(pageable);
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("nom").regex(regex, "i"),
                    Criteria.where("client").regex(regex, "i")));
        }
        if (statut != null) {
            query.addCriteria(Criteria.where("statut").is(statut));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteId").is(siteId));
        }
        AnalyseSupport.appliquerDates(query, dateDebut, dateFin);

        List<Chantier> results = mongoTemplate.find(query, Chantier.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Chantier.class);
        List<ChantierValoriseDto> content = results.stream().map(this::toValorise).toList();
        return new PageResponse<>(content, total);
    }

    public CoutRevientChantierDto detail(String id) {
        Chantier chantier = mongoTemplate.findById(id, Chantier.class);
        if (chantier == null) {
            throw new ResourceNotFoundException("Chantier introuvable : " + id);
        }
        List<MouvementStock> mouvements = mouvementRepository.findByChantierId(id);
        Map<String, ProduitStock> produits = analyseSupport.produitsByIds(mouvements);

        Map<String, CoutRevientChantierDto.LigneChantier> lignes = new LinkedHashMap<>();
        long coutTotal = 0;
        for (MouvementStock m : mouvements) {
            ValorisationSupport.CoutLigne cl = valorisationSupport.coutDe(m, produits);
            long montant = Math.round(m.getQuantite() * cl.coutUnitaire());
            coutTotal += montant;
            CoutRevientChantierDto.LigneChantier ligne = lignes.computeIfAbsent(m.getProduitId(), pid ->
                    CoutRevientChantierDto.LigneChantier.builder()
                            .produitId(pid)
                            .produitCode(m.getProduitCode())
                            .produitLibelle(m.getProduitLibelle())
                            .unite(m.getUnite())
                            .quantite(0)
                            .coutUnitaire(cl.coutUnitaire())
                            .montant(0)
                            .estEstime(false)
                            .build());
            ligne.setQuantite(ligne.getQuantite() + m.getQuantite());
            ligne.setMontant(ligne.getMontant() + montant);
            if (cl.estEstime()) {
                ligne.setEstEstime(true);
            }
        }

        Integer dureeJours = dureeJours(chantier);
        Long coutParJour = dureeJours != null && dureeJours > 0 ? Math.round((double) coutTotal / dureeJours) : null;
        Long coutMoyenSimilaires = coutMoyenChantiersSimilaires(chantier);
        Double ecartPct = (coutMoyenSimilaires == null || coutMoyenSimilaires == 0) ? null
                : (coutTotal - coutMoyenSimilaires) * 100.0 / coutMoyenSimilaires;

        chantier.setCoutTotal(coutTotal);
        chantier.setNbMouvements(mouvements.size());

        return CoutRevientChantierDto.builder()
                .chantier(chantier)
                .lignes(new ArrayList<>(lignes.values()))
                .coutTotal(coutTotal)
                .nbProduits(lignes.size())
                .dureeJours(dureeJours)
                .coutParJour(coutParJour)
                .coutMoyenChantiersSimilaires(coutMoyenSimilaires)
                .ecartPct(ecartPct)
                .build();
    }

    private ChantierValoriseDto toValorise(Chantier chantier) {
        List<MouvementStock> mouvements = mouvementRepository.findByChantierId(chantier.getId());
        Map<String, ProduitStock> produits = analyseSupport.produitsByIds(mouvements);
        long coutTotal = 0;
        java.util.Set<String> produitsDistincts = new java.util.HashSet<>();
        for (MouvementStock m : mouvements) {
            coutTotal += Math.round(m.getQuantite() * valorisationSupport.coutDe(m, produits).coutUnitaire());
            produitsDistincts.add(m.getProduitId());
        }
        Integer dureeJours = dureeJours(chantier);
        Long coutParJour = dureeJours != null && dureeJours > 0 ? Math.round((double) coutTotal / dureeJours) : null;
        return ChantierValoriseDto.builder()
                .id(chantier.getId())
                .reference(chantier.getReference())
                .nom(chantier.getNom())
                .siteNom(chantier.getSiteNom())
                .statut(chantier.getStatut())
                .dateDebut(chantier.getDateDebut())
                .dateFin(chantier.getDateFin())
                .coutTotal(coutTotal)
                .nbProduits(produitsDistincts.size())
                .coutParJour(coutParJour)
                .build();
    }

    /** Moyenne du coût des autres chantiers du même site (null si aucun). */
    private Long coutMoyenChantiersSimilaires(Chantier chantier) {
        if (chantier.getSiteId() == null || chantier.getSiteId().isBlank()) {
            return null;
        }
        List<Chantier> similaires = mongoTemplate.find(
                new Query(Criteria.where("siteId").is(chantier.getSiteId())
                        .and("_id").ne(chantier.getId())), Chantier.class);
        if (similaires.isEmpty()) {
            return null;
        }
        long somme = 0;
        for (Chantier c : similaires) {
            List<MouvementStock> mvts = mouvementRepository.findByChantierId(c.getId());
            Map<String, ProduitStock> produits = analyseSupport.produitsByIds(mvts);
            somme += mvts.stream()
                    .mapToLong(m -> Math.round(m.getQuantite() * valorisationSupport.coutDe(m, produits).coutUnitaire()))
                    .sum();
        }
        return Math.round((double) somme / similaires.size());
    }

    private Integer dureeJours(Chantier chantier) {
        if (chantier.getDateDebut() == null) {
            return null;
        }
        LocalDate fin = chantier.getDateFin() != null ? chantier.getDateFin() : LocalDate.now();
        return (int) (ChronoUnit.DAYS.between(chantier.getDateDebut(), fin) + 1);
    }
}
