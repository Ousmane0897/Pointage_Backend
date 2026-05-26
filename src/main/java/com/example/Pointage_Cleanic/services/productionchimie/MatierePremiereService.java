package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.MatierePremiereDto;
import com.example.Pointage_Cleanic.Mapper.productionchimie.MatierePremiereMapper;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import com.example.Pointage_Cleanic.exception.EmployeAlreadyExistsException;
import com.example.Pointage_Cleanic.exception.EntiteReferenceeException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.productionchimie.MatierePremiereRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatierePremiereService {

    private static final String FORMULATIONS_COLLECTION = "production_chimie_formulations";

    private final MatierePremiereRepository repository;
    private final MatierePremiereMapper mapper;
    private final MongoTemplate mongoTemplate;

    public PageResponse<MatierePremiereDto> list(int page, int size, String q, Boolean sousSeuilCritique, Boolean actif) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nom").ascending());

        Query query = new Query().with(pageable);
        if (q != null && !q.isBlank()) {
            String regex = ".*" + java.util.regex.Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("code").regex(regex, "i"),
                    Criteria.where("nom").regex(regex, "i")
            ));
        }
        if (actif != null) {
            query.addCriteria(Criteria.where("actif").is(actif));
        }
        if (Boolean.TRUE.equals(sousSeuilCritique)) {
            query.addCriteria(Criteria.where("$expr").is(
                    new org.bson.Document("$lte", List.of("$quantiteEnStock", "$seuilCritique"))
            ));
        }

        List<MatierePremiere> results = mongoTemplate.find(query, MatierePremiere.class);
        Query countQuery = Query.of(query).limit(-1).skip(-1);
        long total = mongoTemplate.count(countQuery, MatierePremiere.class);
        List<MatierePremiereDto> content = results.stream().map(mapper::toDto).toList();
        return new PageResponse<>(content, total);
    }

    public List<MatierePremiereDto> listActives() {
        return repository.findByActifTrue().stream().map(mapper::toDto).toList();
    }

    public List<MatierePremiereDto> listSousSeuil() {
        Query query = new Query(Criteria.where("$expr").is(
                new org.bson.Document("$lte", List.of("$quantiteEnStock", "$seuilCritique"))
        ));
        return mongoTemplate.find(query, MatierePremiere.class).stream().map(mapper::toDto).toList();
    }

    public MatierePremiereDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public MatierePremiereDto create(MatierePremiereDto dto, MultipartFile ficheSecurite) throws IOException {
        if (repository.existsByCode(dto.getCode())) {
            throw new EmployeAlreadyExistsException("Code MP déjà utilisé : " + dto.getCode());
        }
        MatierePremiere entity = mapper.toEntity(dto);
        entity.setId(null);
        if (entity.getQuantiteEnStock() == null) {
            entity.setQuantiteEnStock(0.0);
        }
        if (ficheSecurite != null && !ficheSecurite.isEmpty()) {
            entity.setFicheSecurite(ficheSecurite.getBytes());
            entity.setFicheSecuriteNom(ficheSecurite.getOriginalFilename());
            entity.setFicheSecuriteMimeType(ficheSecurite.getContentType());
            entity.setFicheSecuriteTaille(ficheSecurite.getSize());
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return mapper.toDto(repository.save(entity));
    }

    public MatierePremiereDto update(String id, MatierePremiereDto dto, MultipartFile ficheSecurite, boolean supprimerFicheSecurite) throws IOException {
        MatierePremiere entity = loadOrThrow(id);

        if (dto.getCode() != null && !dto.getCode().equals(entity.getCode())
                && repository.existsByCode(dto.getCode())) {
            throw new EmployeAlreadyExistsException("Code MP déjà utilisé : " + dto.getCode());
        }

        mapper.updateEntityFromDto(dto, entity);

        if (supprimerFicheSecurite) {
            entity.setFicheSecurite(null);
            entity.setFicheSecuriteNom(null);
            entity.setFicheSecuriteMimeType(null);
            entity.setFicheSecuriteTaille(null);
        }
        if (ficheSecurite != null && !ficheSecurite.isEmpty()) {
            entity.setFicheSecurite(ficheSecurite.getBytes());
            entity.setFicheSecuriteNom(ficheSecurite.getOriginalFilename());
            entity.setFicheSecuriteMimeType(ficheSecurite.getContentType());
            entity.setFicheSecuriteTaille(ficheSecurite.getSize());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(entity));
    }

    public void delete(String id) {
        MatierePremiere entity = loadOrThrow(id);
        Query referenceQuery = new Query(Criteria.where("ingredients.matierePremiereId").is(id));
        long refs = mongoTemplate.count(referenceQuery, FORMULATIONS_COLLECTION);
        if (refs > 0) {
            throw new EntiteReferenceeException(
                    "Matière première référencée par " + refs + " formulation(s) — suppression interdite");
        }
        repository.delete(entity);
    }

    public MatierePremiere loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Matière première introuvable : " + id));
    }

    public MatierePremiere getFicheSecurite(String id) {
        MatierePremiere entity = loadOrThrow(id);
        if (entity.getFicheSecurite() == null) {
            throw new ResourceNotFoundException("Aucune fiche de sécurité pour la MP : " + id);
        }
        return entity;
    }

    public void deleteFicheSecurite(String id) {
        MatierePremiere entity = loadOrThrow(id);
        entity.setFicheSecurite(null);
        entity.setFicheSecuriteNom(null);
        entity.setFicheSecuriteMimeType(null);
        entity.setFicheSecuriteTaille(null);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }
}