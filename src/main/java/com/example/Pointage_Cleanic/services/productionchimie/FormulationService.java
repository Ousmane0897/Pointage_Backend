package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ComparaisonVersions;
import com.example.Pointage_Cleanic.Dto.productionchimie.FicheFormulationDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.RestaurerVersionPayload;
import com.example.Pointage_Cleanic.Enum.StatutFormulation;
import com.example.Pointage_Cleanic.Mapper.productionchimie.FormulationMapper;
import com.example.Pointage_Cleanic.entities.productionchimie.EtapeFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.FicheFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.IngredientFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.VersionFormulation;
import com.example.Pointage_Cleanic.exception.EmployeAlreadyExistsException;
import com.example.Pointage_Cleanic.exception.EntiteReferenceeException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.productionchimie.FormulationRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormulationService {

    private static final String OF_COLLECTION = "production_chimie_ordres_fabrication";

    private final FormulationRepository repository;
    private final FormulationMapper mapper;
    private final MongoTemplate mongoTemplate;

    public PageResponse<FicheFormulationDto> list(int page, int size, String q, StatutFormulation statut) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nom").ascending());
        Query query = new Query().with(pageable);
        List<Criteria> criterias = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String regex = ".*" + java.util.regex.Pattern.quote(q) + ".*";
            criterias.add(new Criteria().orOperator(
                    Criteria.where("code").regex(regex, "i"),
                    Criteria.where("nom").regex(regex, "i")
            ));
        }
        if (statut != null) {
            criterias.add(Criteria.where("statut").is(statut));
        }
        if (!criterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));
        }
        List<FicheFormulation> results = mongoTemplate.find(query, FicheFormulation.class);
        Query countQuery = Query.of(query).limit(-1).skip(-1);
        long total = mongoTemplate.count(countQuery, FicheFormulation.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public List<FicheFormulationDto> listValidees() {
        return repository.findByStatut(StatutFormulation.VALIDEE).stream().map(mapper::toDto).toList();
    }

    public FicheFormulationDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public FicheFormulation loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Formulation introuvable : " + id));
    }

    public FicheFormulationDto create(FicheFormulationDto dto) {
        if (repository.existsByCode(dto.getCode())) {
            throw new EmployeAlreadyExistsException("Code formulation déjà utilisé : " + dto.getCode());
        }
        FicheFormulation entity = mapper.toEntity(dto);
        entity.setId(null);
        entity.setVersionCourante(1);
        entity.setVersions(new ArrayList<>());
        if (entity.getStatut() == null) {
            entity.setStatut(StatutFormulation.BROUILLON);
        }
        LocalDateTime now = LocalDateTime.now();
        String user = currentUser();
        entity.setCreatedAt(now);
        entity.setCreatedBy(user);
        entity.setUpdatedAt(now);
        entity.setUpdatedBy(user);
        return mapper.toDto(repository.save(entity));
    }

    public FicheFormulationDto update(String id, FicheFormulationDto dto) {
        FicheFormulation entity = loadOrThrow(id);

        if (dto.getCode() != null && !dto.getCode().equals(entity.getCode())
                && repository.existsByCode(dto.getCode())) {
            throw new EmployeAlreadyExistsException("Code formulation déjà utilisé : " + dto.getCode());
        }

        if (entity.getVersions() == null) {
            entity.setVersions(new ArrayList<>());
        }
        entity.getVersions().add(snapshotVersionCourante(entity, dto.getMotif()));

        mapper.updateEntityFromDto(dto, entity);

        entity.setVersionCourante(entity.getVersionCourante() + 1);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(currentUser());
        return mapper.toDto(repository.save(entity));
    }

    public void delete(String id) {
        FicheFormulation entity = loadOrThrow(id);
        Query ref = new Query(Criteria.where("formulationId").is(id));
        long refs = mongoTemplate.count(ref, OF_COLLECTION);
        if (refs > 0) {
            throw new EntiteReferenceeException("Formulation référencée par " + refs + " ordre(s) de fabrication — suppression interdite");
        }
        repository.delete(entity);
    }

    public FicheFormulationDto restaurerVersion(String id, Integer numero, RestaurerVersionPayload payload) {
        FicheFormulation entity = loadOrThrow(id);

        VersionFormulation cible = entity.getVersions() == null
                ? null
                : entity.getVersions().stream()
                        .filter(v -> Objects.equals(v.getNumero(), numero))
                        .findFirst()
                        .orElse(null);
        if (cible == null) {
            throw new ResourceNotFoundException("Version " + numero + " introuvable pour la formulation " + id);
        }

        if (entity.getVersions() == null) {
            entity.setVersions(new ArrayList<>());
        }
        String motif = "Restauration v" + numero;
        if (payload != null && payload.getMotif() != null && !payload.getMotif().isBlank()) {
            motif = motif + " — " + payload.getMotif();
        }
        VersionFormulation snapshot = snapshotVersionCourante(entity, motif);
        entity.getVersions().add(snapshot);

        entity.setIngredients(copyIngredients(cible.getIngredients()));
        entity.setEtapes(copyEtapes(cible.getEtapes()));
        entity.setDureePeremptionJours(cible.getDureePeremptionJours());

        entity.setVersionCourante(entity.getVersionCourante() + 1);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(currentUser());
        return mapper.toDto(repository.save(entity));
    }

    public ComparaisonVersions comparerVersions(String id, Integer v1, Integer v2) {
        FicheFormulation entity = loadOrThrow(id);
        VersionFormulation version1 = findVersion(entity, v1);
        VersionFormulation version2 = findVersion(entity, v2);

        return ComparaisonVersions.builder()
                .v1(v1).v2(v2)
                .version1(version1).version2(version2)
                .diffIngredients(diffIngredients(version1.getIngredients(), version2.getIngredients()))
                .diffEtapes(diffEtapes(version1.getEtapes(), version2.getEtapes()))
                .diffPeremption(Objects.equals(version1.getDureePeremptionJours(), version2.getDureePeremptionJours())
                        ? null
                        : new ComparaisonVersions.DiffPeremption(version1.getDureePeremptionJours(), version2.getDureePeremptionJours()))
                .build();
    }

    private VersionFormulation snapshotVersionCourante(FicheFormulation entity, String motif) {
        return VersionFormulation.builder()
                .numero(entity.getVersionCourante())
                .dateModification(LocalDateTime.now())
                .auteur(currentUser())
                .motif(motif)
                .ingredients(copyIngredients(entity.getIngredients()))
                .etapes(copyEtapes(entity.getEtapes()))
                .dureePeremptionJours(entity.getDureePeremptionJours())
                .build();
    }

    private VersionFormulation findVersion(FicheFormulation entity, Integer numero) {
        if (Objects.equals(numero, entity.getVersionCourante())) {
            return VersionFormulation.builder()
                    .numero(entity.getVersionCourante())
                    .ingredients(entity.getIngredients())
                    .etapes(entity.getEtapes())
                    .dureePeremptionJours(entity.getDureePeremptionJours())
                    .auteur(entity.getUpdatedBy())
                    .dateModification(entity.getUpdatedAt())
                    .build();
        }
        return Optional.ofNullable(entity.getVersions()).orElse(List.of()).stream()
                .filter(v -> Objects.equals(v.getNumero(), numero))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Version " + numero + " introuvable"));
    }

    private ComparaisonVersions.DiffIngredients diffIngredients(List<IngredientFormulation> v1, List<IngredientFormulation> v2) {
        List<IngredientFormulation> a = v1 == null ? List.of() : v1;
        List<IngredientFormulation> b = v2 == null ? List.of() : v2;
        Map<String, IngredientFormulation> aMap = new HashMap<>();
        for (IngredientFormulation i : a) aMap.put(i.getMatierePremiereId(), i);
        Map<String, IngredientFormulation> bMap = new HashMap<>();
        for (IngredientFormulation i : b) bMap.put(i.getMatierePremiereId(), i);

        List<IngredientFormulation> added = b.stream()
                .filter(i -> !aMap.containsKey(i.getMatierePremiereId())).toList();
        List<IngredientFormulation> removed = a.stream()
                .filter(i -> !bMap.containsKey(i.getMatierePremiereId())).toList();
        List<IngredientFormulation> modified = b.stream()
                .filter(i -> aMap.containsKey(i.getMatierePremiereId()))
                .filter(i -> {
                    IngredientFormulation old = aMap.get(i.getMatierePremiereId());
                    return !Objects.equals(old.getDosage(), i.getDosage())
                            || !Objects.equals(old.getUnite(), i.getUnite())
                            || !Objects.equals(old.getOrdre(), i.getOrdre())
                            || !Objects.equals(old.getRemarque(), i.getRemarque());
                })
                .toList();
        return new ComparaisonVersions.DiffIngredients(added, removed, modified);
    }

    private ComparaisonVersions.DiffEtapes diffEtapes(List<EtapeFormulation> v1, List<EtapeFormulation> v2) {
        List<EtapeFormulation> a = v1 == null ? List.of() : v1;
        List<EtapeFormulation> b = v2 == null ? List.of() : v2;
        Map<Integer, EtapeFormulation> aMap = new HashMap<>();
        for (EtapeFormulation e : a) aMap.put(e.getOrdre(), e);
        Map<Integer, EtapeFormulation> bMap = new HashMap<>();
        for (EtapeFormulation e : b) bMap.put(e.getOrdre(), e);

        List<EtapeFormulation> added = b.stream().filter(e -> !aMap.containsKey(e.getOrdre())).toList();
        List<EtapeFormulation> removed = a.stream().filter(e -> !bMap.containsKey(e.getOrdre())).toList();
        List<EtapeFormulation> modified = b.stream()
                .filter(e -> aMap.containsKey(e.getOrdre()))
                .filter(e -> {
                    EtapeFormulation old = aMap.get(e.getOrdre());
                    return !Objects.equals(old.getLibelle(), e.getLibelle())
                            || !Objects.equals(old.getDureeMinutes(), e.getDureeMinutes())
                            || !Objects.equals(old.getTemperature(), e.getTemperature())
                            || !Objects.equals(old.getPression(), e.getPression())
                            || !Objects.equals(old.getVitesseAgitation(), e.getVitesseAgitation())
                            || !Objects.equals(old.getDureeReposMinutes(), e.getDureeReposMinutes())
                            || !Objects.equals(old.getInstructions(), e.getInstructions());
                })
                .toList();
        return new ComparaisonVersions.DiffEtapes(added, removed, modified);
    }

    private List<IngredientFormulation> copyIngredients(List<IngredientFormulation> source) {
        if (source == null) return new ArrayList<>();
        List<IngredientFormulation> copy = new ArrayList<>(source.size());
        for (IngredientFormulation i : source) {
            copy.add(IngredientFormulation.builder()
                    .matierePremiereId(i.getMatierePremiereId())
                    .matierePremiereNom(i.getMatierePremiereNom())
                    .dosage(i.getDosage())
                    .unite(i.getUnite())
                    .ordre(i.getOrdre())
                    .remarque(i.getRemarque())
                    .build());
        }
        return copy;
    }

    private List<EtapeFormulation> copyEtapes(List<EtapeFormulation> source) {
        if (source == null) return new ArrayList<>();
        List<EtapeFormulation> copy = new ArrayList<>(source.size());
        for (EtapeFormulation e : source) {
            copy.add(EtapeFormulation.builder()
                    .ordre(e.getOrdre())
                    .libelle(e.getLibelle())
                    .dureeMinutes(e.getDureeMinutes())
                    .temperature(e.getTemperature())
                    .pression(e.getPression())
                    .vitesseAgitation(e.getVitesseAgitation())
                    .dureeReposMinutes(e.getDureeReposMinutes())
                    .instructions(e.getInstructions())
                    .build());
        }
        return copy;
    }

    private String currentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(a -> a.getName())
                .filter(name -> !"anonymousUser".equals(name))
                .orElse("system");
    }
}