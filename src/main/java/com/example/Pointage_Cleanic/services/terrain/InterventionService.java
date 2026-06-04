package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.FicheInterventionDto;
import com.example.Pointage_Cleanic.Dto.terrain.PhotoMetaDto;
import com.example.Pointage_Cleanic.Enum.terrain.MomentPhoto;
import com.example.Pointage_Cleanic.Enum.terrain.StatutIntervention;
import com.example.Pointage_Cleanic.Mapper.terrain.FicheInterventionMapper;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.terrain.FicheIntervention;
import com.example.Pointage_Cleanic.entities.terrain.PhotoInterventionFichier;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.terrain.FicheInterventionRepository;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterventionService {

    private final FicheInterventionRepository repository;
    private final FicheInterventionMapper mapper;
    private final MongoTemplate mongoTemplate;
    private final ReferentielRhService referentielRh;
    private final SiteClientService siteService;
    private final CompteurTerrainService compteur;
    private final InterventionPdfService pdfService;

    public PageResponse<FicheInterventionDto> list(int page, int size, LocalDate dateDebut, LocalDate dateFin,
                                                   String siteId, String employeId, StatutIntervention statut) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateDebut").descending());
        Query query = new Query().with(pageable);
        if (dateDebut != null) query.addCriteria(Criteria.where("dateDebut").gte(dateDebut.atStartOfDay()));
        if (dateFin != null) query.addCriteria(Criteria.where("dateDebut").lte(dateFin.atTime(LocalTime.MAX)));
        if (siteId != null && !siteId.isBlank()) query.addCriteria(Criteria.where("siteId").is(siteId));
        if (employeId != null && !employeId.isBlank()) query.addCriteria(Criteria.where("employeId").is(employeId));
        if (statut != null) query.addCriteria(Criteria.where("statut").is(statut));

        List<FicheIntervention> results = mongoTemplate.find(query, FicheIntervention.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), FicheIntervention.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public FicheInterventionDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public FicheInterventionDto create(FicheInterventionDto dto, MultipartFile[] photos) throws IOException {
        FicheIntervention entity = mapper.toEntity(dto);
        entity.setId(null);
        denormaliser(entity);

        entity.setNumero(compteur.genererNumeroIntervention(LocalDate.now()));
        if (entity.getStatut() == null) {
            entity.setStatut(StatutIntervention.BROUILLON);
        }
        entity.setPhotos(construireNouvellesPhotos(photos, dto.getPhotosMeta()));
        entity.setDuree(calculerDuree(entity.getDateDebut(), entity.getDateFin()));

        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return mapper.toDto(repository.save(entity));
    }

    public FicheInterventionDto update(String id, FicheInterventionDto dto, MultipartFile[] photos) throws IOException {
        FicheIntervention entity = loadOrThrow(id);

        List<PhotoInterventionFichier> existantes = entity.getPhotos() == null
                ? new ArrayList<>() : entity.getPhotos();

        mapper.updateEntityFromDto(dto, entity);
        denormaliser(entity);

        // Photos conservées (indices) + nouvelles photos uploadées
        List<PhotoInterventionFichier> finales = new ArrayList<>();
        List<Integer> conservees = dto.getPhotosConservees();
        if (conservees == null) {
            finales.addAll(existantes); // aucune indication → on conserve tout l'existant
        } else {
            for (Integer idx : conservees) {
                if (idx != null && idx >= 0 && idx < existantes.size()) {
                    finales.add(existantes.get(idx));
                }
            }
        }
        finales.addAll(construireNouvellesPhotos(photos, dto.getPhotosMeta()));
        entity.setPhotos(finales);

        entity.setDuree(calculerDuree(entity.getDateDebut(), entity.getDateFin()));
        entity.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(entity));
    }

    public void delete(String id) {
        repository.delete(loadOrThrow(id));
    }

    public PhotoInterventionFichier getPhoto(String id, int index) {
        FicheIntervention fiche = loadOrThrow(id);
        if (fiche.getPhotos() == null || index < 0 || index >= fiche.getPhotos().size()) {
            throw new ResourceNotFoundException("Photo introuvable : intervention " + id + " index " + index);
        }
        return fiche.getPhotos().get(index);
    }

    public byte[] genererPdf(String id) {
        return pdfService.generer(loadOrThrow(id));
    }

    public FicheIntervention loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable : " + id));
    }

    // ───────────────────────── Helpers ─────────────────────────

    private void denormaliser(FicheIntervention entity) {
        DossierEmploye employe = referentielRh.valideEtCharge(entity.getEmployeId());
        entity.setEmployeMatricule(employe.getMatricule());
        entity.setEmployeNom(ReferentielRhService.nomComplet(employe));

        SiteClient site = siteService.loadOrThrow(entity.getSiteId());
        entity.setSiteCode(site.getCode());
        entity.setSiteNom(site.getNom());
    }

    private List<PhotoInterventionFichier> construireNouvellesPhotos(MultipartFile[] photos,
                                                                     List<PhotoMetaDto> photosMeta) throws IOException {
        List<PhotoInterventionFichier> result = new ArrayList<>();
        if (photos == null) {
            return result;
        }
        for (int i = 0; i < photos.length; i++) {
            MultipartFile f = photos[i];
            if (f == null || f.isEmpty()) continue;
            PhotoMetaDto meta = (photosMeta != null && i < photosMeta.size()) ? photosMeta.get(i) : null;
            result.add(PhotoInterventionFichier.builder()
                    .nomFichier(f.getOriginalFilename())
                    .mimeType(f.getContentType())
                    .tailleOctets(f.getSize())
                    .moment(meta != null && meta.getMoment() != null ? meta.getMoment() : MomentPhoto.AUTRE)
                    .legende(meta != null ? meta.getLegende() : null)
                    .contenu(f.getBytes())
                    .build());
        }
        return result;
    }

    private Integer calculerDuree(LocalDateTime debut, LocalDateTime fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            return null;
        }
        return (int) Duration.between(debut, fin).toMinutes();
    }
}