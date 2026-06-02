package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.PointageTerrainDto;
import com.example.Pointage_Cleanic.Enum.terrain.StatutPointage;
import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteTerrain;
import com.example.Pointage_Cleanic.Enum.terrain.TypePointage;
import com.example.Pointage_Cleanic.Mapper.terrain.PointageTerrainMapper;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.entities.terrain.PointageTerrain;
import com.example.Pointage_Cleanic.entities.terrain.PositionGps;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.terrain.PointageTerrainRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.example.Pointage_Cleanic.services.terrain.TerrainConstantes.PRECISION_GPS_MAX_M;
import static com.example.Pointage_Cleanic.services.terrain.TerrainConstantes.RAYON_TOLERANCE_DEFAUT_M;

@Service
@RequiredArgsConstructor
public class PointageTerrainService {

    private final PointageTerrainRepository repository;
    private final PointageTerrainMapper mapper;
    private final MongoTemplate mongoTemplate;
    private final ReferentielRhService referentielRh;
    private final SiteClientService siteService;
    private final HaversineService haversine;
    private final AlerteService alerteService;
    private final TerrainNotificationService notification;

    public PointageTerrainDto create(PointageTerrainDto dto) {
        PointageTerrain entity = mapper.toEntity(dto);
        entity.setId(null);

        DossierEmploye employe = referentielRh.valideEtCharge(entity.getEmployeId());
        entity.setEmployeMatricule(employe.getMatricule());
        entity.setEmployeNom(ReferentielRhService.nomComplet(employe));

        SiteClient site = siteService.loadOrThrow(entity.getSiteId());
        entity.setSiteCode(site.getCode());
        entity.setSiteNom(site.getNom());

        // Anti-spoof : horodatage et géolocalisation recalculés côté serveur.
        LocalDateTime now = LocalDateTime.now();
        entity.setDatePointage(now);
        entity.setCreatedAt(now);

        PositionGps pos = entity.getPositionAgent();
        StatutPointage statut = evaluer(pos, site, entity);
        entity.setStatut(statut);

        PointageTerrain saved = repository.save(entity);
        PointageTerrainDto result = mapper.toDto(saved);

        if (statut == StatutPointage.HORS_ZONE) {
            alerteService.creerAlerte(
                    TypeAlerteTerrain.POINTAGE_HORS_ZONE,
                    saved.getEmployeId(), saved.getEmployeMatricule(), saved.getEmployeNom(),
                    saved.getSiteId(), saved.getSiteNom(),
                    saved.getAffectationId(), saved.getId(), saved.getDatePointage());
        }

        notification.diffuserPointage(result);
        return result;
    }

    /** Calcule distanceM (par effet de bord) et renvoie le statut anti-spoof. */
    private StatutPointage evaluer(PositionGps pos, SiteClient site, PointageTerrain entity) {
        if (pos == null || pos.getLatitude() == null || pos.getLongitude() == null
                || site.getCoordonnees() == null
                || site.getCoordonnees().getLatitude() == null
                || site.getCoordonnees().getLongitude() == null) {
            return StatutPointage.GPS_INDISPONIBLE;
        }

        double distance = haversine.distanceM(
                pos.getLatitude(), pos.getLongitude(),
                site.getCoordonnees().getLatitude(), site.getCoordonnees().getLongitude());
        entity.setDistanceM(distance);

        if (pos.getPrecisionM() != null && pos.getPrecisionM() > PRECISION_GPS_MAX_M) {
            return StatutPointage.GPS_IMPRECIS;
        }
        int tolerance = site.getRayonToleranceM() == null ? RAYON_TOLERANCE_DEFAUT_M : site.getRayonToleranceM();
        return distance <= tolerance ? StatutPointage.SUR_SITE : StatutPointage.HORS_ZONE;
    }

    public List<PointageTerrainDto> jour(String employeId) {
        LocalDate today = LocalDate.now();
        return repository.findByEmployeIdAndDatePointageBetween(
                        employeId, today.atStartOfDay(), today.atTime(LocalTime.MAX))
                .stream().map(mapper::toDto).toList();
    }

    public List<PointageTerrainDto> aujourdHui() {
        LocalDate today = LocalDate.now();
        return repository.findByDatePointageBetween(today.atStartOfDay(), today.atTime(LocalTime.MAX))
                .stream().map(mapper::toDto).toList();
    }

    public PageResponse<PointageTerrainDto> historique(int page, int size, LocalDate dateDebut, LocalDate dateFin,
                                                       String employeId, String siteId,
                                                       TypePointage type, StatutPointage statut) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("datePointage").descending());
        Query query = new Query().with(pageable);
        if (dateDebut != null) query.addCriteria(Criteria.where("datePointage").gte(dateDebut.atStartOfDay()));
        if (dateFin != null) query.addCriteria(Criteria.where("datePointage").lte(dateFin.atTime(LocalTime.MAX)));
        if (employeId != null && !employeId.isBlank()) query.addCriteria(Criteria.where("employeId").is(employeId));
        if (siteId != null && !siteId.isBlank()) query.addCriteria(Criteria.where("siteId").is(siteId));
        if (type != null) query.addCriteria(Criteria.where("type").is(type));
        if (statut != null) query.addCriteria(Criteria.where("statut").is(statut));

        List<PointageTerrain> results = mongoTemplate.find(query, PointageTerrain.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), PointageTerrain.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public PointageTerrainDto getById(String id) {
        return mapper.toDto(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pointage introuvable : " + id)));
    }
}