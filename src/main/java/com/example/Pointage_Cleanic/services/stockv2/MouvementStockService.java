package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MouvementStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Mapper.stockv2.MouvementStockMapper;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
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
import java.util.List;
import java.util.regex.Pattern;

/**
 * Consultation des mouvements de stock — <b>lecture seule</b>.
 *
 * <p>⚠ Ce service ne dépend volontairement <b>plus</b> de {@link StockBalanceService} : il n'a donc
 * aucun moyen de modifier un solde. La garantie « aucun mouvement n'affecte le stock hors du circuit
 * de validation » est ainsi structurelle, et non la simple absence d'une route.
 *
 * <p>Un {@code create} appliquait auparavant les deltas directement, exposé par
 * {@code POST /api/stock/mouvements} : voir {@code MouvementStockController} pour les chemins
 * légitimes qui subsistent.
 */
@Service
@RequiredArgsConstructor
public class MouvementStockService {

    private final MouvementStockRepository repository;
    private final MouvementStockMapper mapper;
    private final MongoTemplate mongoTemplate;

    public PageResponse<MouvementStockDto> list(int page, int size, String q, String produitId,
                                                TypeMouvement type, MotifMouvement motif, String siteId,
                                                LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        Query query = new Query().with(pageable);

        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("produitCode").regex(regex, "i"),
                    Criteria.where("produitLibelle").regex(regex, "i")
            ));
        }
        if (produitId != null && !produitId.isBlank()) {
            query.addCriteria(Criteria.where("produitId").is(produitId));
        }
        if (type != null) {
            query.addCriteria(Criteria.where("type").is(type));
        }
        if (motif != null) {
            query.addCriteria(Criteria.where("motif").is(motif));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("siteSourceId").is(siteId),
                    Criteria.where("siteDestinationId").is(siteId)
            ));
        }
        if (dateDebut != null && dateFin != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut).lte(dateFin));
        } else if (dateDebut != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut));
        } else if (dateFin != null) {
            query.addCriteria(Criteria.where("date").lte(dateFin));
        }

        List<MouvementStock> results = mongoTemplate.find(query, MouvementStock.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), MouvementStock.class);
        List<MouvementStockDto> content = results.stream().map(mapper::toDto).toList();
        return new PageResponse<>(content, total);
    }

    public MouvementStockDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public MouvementStock loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mouvement introuvable : " + id));
    }
}
