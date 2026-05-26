package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.FormatConditionnementDto;
import com.example.Pointage_Cleanic.Enum.TypeContenant;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.example.Pointage_Cleanic.Mapper.productionchimie.FormatConditionnementMapper;
import com.example.Pointage_Cleanic.entities.productionchimie.FormatConditionnement;
import com.example.Pointage_Cleanic.exception.EmployeAlreadyExistsException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.productionchimie.FormatConditionnementRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FormatConditionnementService {

    private final FormatConditionnementRepository repository;
    private final FormatConditionnementMapper mapper;
    private final MongoTemplate mongoTemplate;

    public PageResponse<FormatConditionnementDto> list(int page, int size, String q, TypeContenant typeContenant, Boolean actif) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("libelle").ascending());
        Query query = new Query().with(pageable);

        List<Criteria> criterias = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String regex = ".*" + java.util.regex.Pattern.quote(q) + ".*";
            criterias.add(new Criteria().orOperator(
                    Criteria.where("code").regex(regex, "i"),
                    Criteria.where("libelle").regex(regex, "i")
            ));
        }
        if (typeContenant != null) {
            criterias.add(Criteria.where("typeContenant").is(typeContenant));
        }
        if (actif != null) {
            criterias.add(Criteria.where("actif").is(actif));
        }
        if (!criterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));
        }

        List<FormatConditionnement> results = mongoTemplate.find(query, FormatConditionnement.class);
        Query countQuery = Query.of(query).limit(-1).skip(-1);
        long total = mongoTemplate.count(countQuery, FormatConditionnement.class);
        List<FormatConditionnementDto> content = results.stream().map(mapper::toDto).toList();
        return new PageResponse<>(content, total);
    }

    public List<FormatConditionnementDto> listActifs() {
        return repository.findByActifTrue().stream().map(mapper::toDto).toList();
    }

    public FormatConditionnementDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public FormatConditionnementDto create(FormatConditionnementDto dto) {
        if (repository.existsByCode(dto.getCode())) {
            throw new EmployeAlreadyExistsException("Code format déjà utilisé : " + dto.getCode());
        }
        validateUniteVolume(dto.getUniteVolume());
        FormatConditionnement entity = mapper.toEntity(dto);
        entity.setId(null);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return mapper.toDto(repository.save(entity));
    }

    public FormatConditionnementDto update(String id, FormatConditionnementDto dto) {
        FormatConditionnement entity = loadOrThrow(id);
        if (dto.getCode() != null && !dto.getCode().equals(entity.getCode())
                && repository.existsByCode(dto.getCode())) {
            throw new EmployeAlreadyExistsException("Code format déjà utilisé : " + dto.getCode());
        }
        if (dto.getUniteVolume() != null) {
            validateUniteVolume(dto.getUniteVolume());
        }
        mapper.updateEntityFromDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(entity));
    }

    public void delete(String id) {
        FormatConditionnement entity = loadOrThrow(id);
        repository.delete(entity);
    }

    private FormatConditionnement loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Format de conditionnement introuvable : " + id));
    }

    private void validateUniteVolume(UniteChimie unite) {
        if (unite != UniteChimie.L && unite != UniteChimie.ML) {
            throw new IllegalArgumentException("uniteVolume doit être L ou ML, reçu : " + unite);
        }
    }
}