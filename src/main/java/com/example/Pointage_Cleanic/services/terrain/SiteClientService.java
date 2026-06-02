package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.SiteClientDto;
import com.example.Pointage_Cleanic.Enum.terrain.FrequencePassage;
import com.example.Pointage_Cleanic.Mapper.terrain.SiteClientMapper;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.TerrainConflitException;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SiteClientService {

    private final SiteClientRepository repository;
    private final SiteClientMapper mapper;
    private final MongoTemplate mongoTemplate;

    public PageResponse<SiteClientDto> list(int page, int size, String q, String ville,
                                            FrequencePassage frequencePassage, Boolean actif) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nom").ascending());
        Query query = new Query().with(pageable);

        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("code").regex(regex, "i"),
                    Criteria.where("nom").regex(regex, "i"),
                    Criteria.where("raisonSociale").regex(regex, "i")
            ));
        }
        if (ville != null && !ville.isBlank()) {
            query.addCriteria(Criteria.where("ville").regex(".*" + Pattern.quote(ville) + ".*", "i"));
        }
        if (frequencePassage != null) {
            query.addCriteria(Criteria.where("frequencePassage").is(frequencePassage));
        }
        if (actif != null) {
            query.addCriteria(Criteria.where("actif").is(actif));
        }

        List<SiteClient> results = mongoTemplate.find(query, SiteClient.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), SiteClient.class);
        List<SiteClientDto> content = results.stream().map(mapper::toDto).toList();
        return new PageResponse<>(content, total);
    }

    public List<SiteClientDto> listActifs() {
        return repository.findByActifTrue().stream().map(mapper::toDto).toList();
    }

    public SiteClientDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public SiteClientDto create(SiteClientDto dto, MultipartFile cahierDesCharges) throws IOException {
        if (dto.getCode() != null && repository.existsByCode(dto.getCode())) {
            throw new TerrainConflitException("Code site déjà utilisé : " + dto.getCode());
        }
        SiteClient entity = mapper.toEntity(dto);
        entity.setId(null);
        appliquerFichier(entity, cahierDesCharges);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return mapper.toDto(repository.save(entity));
    }

    public SiteClientDto update(String id, SiteClientDto dto, MultipartFile cahierDesCharges) throws IOException {
        SiteClient entity = loadOrThrow(id);
        if (dto.getCode() != null && !dto.getCode().equals(entity.getCode())
                && repository.existsByCode(dto.getCode())) {
            throw new TerrainConflitException("Code site déjà utilisé : " + dto.getCode());
        }
        mapper.updateEntityFromDto(dto, entity);
        appliquerFichier(entity, cahierDesCharges);
        entity.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(entity));
    }

    public void delete(String id) {
        repository.delete(loadOrThrow(id));
    }

    public SiteClient getCahierCharges(String id) {
        SiteClient entity = loadOrThrow(id);
        if (entity.getCahierDesChargesFichier() == null) {
            throw new ResourceNotFoundException("Aucun cahier des charges pour le site : " + id);
        }
        return entity;
    }

    public SiteClient loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Site introuvable : " + id));
    }

    private void appliquerFichier(SiteClient entity, MultipartFile cahierDesCharges) throws IOException {
        if (cahierDesCharges != null && !cahierDesCharges.isEmpty()) {
            entity.setCahierDesChargesFichier(cahierDesCharges.getBytes());
            entity.setCahierDesChargesNomFichier(cahierDesCharges.getOriginalFilename());
            entity.setCahierDesChargesMimeType(cahierDesCharges.getContentType());
        }
    }
}