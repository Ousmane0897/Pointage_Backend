package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.LotDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.TracabiliteLot;
import com.example.Pointage_Cleanic.Enum.StatutControleLot;
import com.example.Pointage_Cleanic.Enum.StatutStockLot;
import com.example.Pointage_Cleanic.Mapper.productionchimie.LotMapper;
import com.example.Pointage_Cleanic.entities.productionchimie.FicheFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.Lot;
import com.example.Pointage_Cleanic.entities.productionchimie.OrdreFabrication;
import com.example.Pointage_Cleanic.entities.productionchimie.VersionFormulation;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.productionchimie.LotRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LotService {

    private final LotRepository repository;
    private final LotMapper mapper;
    private final OrdreFabricationService ordreFabricationService;
    private final FormulationService formulationService;
    private final MongoTemplate mongoTemplate;

    public PageResponse<LotDto> list(int page, int size, String q, String produitNom, String formulationId,
                                     StatutControleLot statutControle, StatutStockLot statutStock,
                                     LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateFabrication"));
        Query query = new Query().with(pageable);
        List<Criteria> criterias = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String regex = ".*" + java.util.regex.Pattern.quote(q) + ".*";
            criterias.add(new Criteria().orOperator(
                    Criteria.where("numero").regex(regex, "i"),
                    Criteria.where("produitNom").regex(regex, "i")
            ));
        }
        if (produitNom != null && !produitNom.isBlank()) criterias.add(Criteria.where("produitNom").is(produitNom));
        if (formulationId != null && !formulationId.isBlank()) criterias.add(Criteria.where("formulationId").is(formulationId));
        if (statutControle != null) criterias.add(Criteria.where("statutControle").is(statutControle));
        if (statutStock != null) criterias.add(Criteria.where("statutStock").is(statutStock));
        if (dateDebut != null) criterias.add(Criteria.where("dateFabrication").gte(dateDebut.atStartOfDay()));
        if (dateFin != null) criterias.add(Criteria.where("dateFabrication").lte(dateFin.atTime(23, 59, 59)));
        if (!criterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));
        }
        List<Lot> results = mongoTemplate.find(query, Lot.class);
        Query countQuery = Query.of(query).limit(-1).skip(-1);
        long total = mongoTemplate.count(countQuery, Lot.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public LotDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public Lot loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + id));
    }

    public TracabiliteLot tracabilite(String id) {
        Lot lot = loadOrThrow(id);
        OrdreFabrication of = lot.getOrdreFabricationId() == null
                ? null
                : ordreFabricationService.loadOrThrow(lot.getOrdreFabricationId());
        FicheFormulation form = lot.getFormulationId() == null
                ? null
                : formulationService.loadOrThrow(lot.getFormulationId());

        VersionFormulation versionSnapshot = null;
        if (form != null) {
            Integer numero = lot.getFormulationVersion();
            if (numero != null && Objects.equals(numero, form.getVersionCourante())) {
                versionSnapshot = VersionFormulation.builder()
                        .numero(form.getVersionCourante())
                        .ingredients(form.getIngredients())
                        .etapes(form.getEtapes())
                        .dureePeremptionJours(form.getDureePeremptionJours())
                        .auteur(form.getUpdatedBy())
                        .dateModification(form.getUpdatedAt())
                        .build();
            } else if (form.getVersions() != null) {
                versionSnapshot = form.getVersions().stream()
                        .filter(v -> Objects.equals(v.getNumero(), numero))
                        .findFirst()
                        .orElse(null);
            }
        }

        return TracabiliteLot.builder()
                .lot(mapper.toDto(lot))
                .ordreFabrication(of == null ? null : TracabiliteLot.OrdreFabricationResume.builder()
                        .id(of.getId())
                        .numero(of.getNumero())
                        .operateurNom(of.getOperateurResponsableNom())
                        .dateLancementEffective(of.getDateLancementEffective())
                        .dateFin(of.getDateFin())
                        .build())
                .formulation(form == null ? null : TracabiliteLot.FormulationResume.builder()
                        .id(form.getId())
                        .code(form.getCode())
                        .nom(form.getNom())
                        .version(versionSnapshot)
                        .build())
                .consommationsMp(of == null ? List.of() : of.getConsommationMp())
                .controleQualiteId(lot.getControleQualiteId())
                .build();
    }
}
