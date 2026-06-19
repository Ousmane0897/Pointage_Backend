package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.LigneCoutMouvementDto;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Mapper.stockv2.MouvementStockMapper;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mouvements valorisés (Stock v2 7.6) : chaque mouvement avec son coût unitaire (snapshot ou
 * reconstitué) et sa valeur.
 */
@Service
@RequiredArgsConstructor
public class CoutMouvementService {

    private final MouvementStockMapper mapper;
    private final AnalyseSupport analyseSupport;
    private final ValorisationSupport valorisationSupport;
    private final MongoTemplate mongoTemplate;

    public PageResponse<LigneCoutMouvementDto> list(int page, int size, String q, String produitId,
                                                    TypeMouvement type, String siteId,
                                                    LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        Query query = new Query().with(pageable);

        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("bonReference").regex(regex, "i"),
                    Criteria.where("produitCode").regex(regex, "i"),
                    Criteria.where("produitLibelle").regex(regex, "i")));
        }
        if (produitId != null && !produitId.isBlank()) {
            query.addCriteria(Criteria.where("produitId").is(produitId));
        }
        if (type != null) {
            query.addCriteria(Criteria.where("type").is(type));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("siteSourceId").is(siteId),
                    Criteria.where("siteDestinationId").is(siteId)));
        }
        AnalyseSupport.appliquerDates(query, dateDebut, dateFin);

        List<MouvementStock> results = mongoTemplate.find(query, MouvementStock.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), MouvementStock.class);
        Map<String, ProduitStock> produits = analyseSupport.produitsByIds(results);

        List<LigneCoutMouvementDto> content = results.stream().map(m -> {
            ValorisationSupport.CoutLigne cl = valorisationSupport.coutDe(m, produits);
            return LigneCoutMouvementDto.builder()
                    .mouvement(mapper.toDto(m))
                    .coutUnitaire(cl.coutUnitaire())
                    .valeur(Math.round(m.getQuantite() * cl.coutUnitaire()))
                    .estEstime(cl.estEstime())
                    .build();
        }).toList();
        return new PageResponse<>(content, total);
    }
}
