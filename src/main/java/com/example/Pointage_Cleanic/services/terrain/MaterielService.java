package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AlerteMaintenance;
import com.example.Pointage_Cleanic.Enum.terrain.NiveauAlerteMaintenance;
import com.example.Pointage_Cleanic.Enum.terrain.StatutMateriel;
import com.example.Pointage_Cleanic.Enum.terrain.TypeEvenementMateriel;
import com.example.Pointage_Cleanic.Enum.terrain.TypeMateriel;
import com.example.Pointage_Cleanic.entities.terrain.EvenementMateriel;
import com.example.Pointage_Cleanic.entities.terrain.MaintenanceProgrammee;
import com.example.Pointage_Cleanic.entities.terrain.Materiel;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.TerrainConflitException;
import com.example.Pointage_Cleanic.repositories.terrain.EvenementMaterielRepository;
import com.example.Pointage_Cleanic.repositories.terrain.MaintenanceProgrammeeRepository;
import com.example.Pointage_Cleanic.repositories.terrain.MaterielRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MaterielService {

    private final MaterielRepository repository;
    private final EvenementMaterielRepository evenementRepository;
    private final MaintenanceProgrammeeRepository maintenanceRepository;
    private final MongoTemplate mongoTemplate;
    private final SiteClientService siteService;

    // ───────────────────────── CRUD ─────────────────────────

    public PageResponse<Materiel> list(int page, int size, String q, TypeMateriel type,
                                       StatutMateriel statut, String siteAffecteId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("nom").ascending());
        Query query = new Query().with(pageable);
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("code").regex(regex, "i"),
                    Criteria.where("nom").regex(regex, "i"),
                    Criteria.where("numeroSerie").regex(regex, "i")
            ));
        }
        if (type != null) query.addCriteria(Criteria.where("type").is(type));
        if (statut != null) query.addCriteria(Criteria.where("statut").is(statut));
        if (siteAffecteId != null && !siteAffecteId.isBlank())
            query.addCriteria(Criteria.where("siteAffecteId").is(siteAffecteId));

        List<Materiel> results = mongoTemplate.find(query, Materiel.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Materiel.class);
        return new PageResponse<>(results, total);
    }

    public Materiel getById(String id) {
        return loadOrThrow(id);
    }

    public Materiel create(Materiel materiel) {
        materiel.setId(null);
        if (materiel.getCode() != null && repository.existsByCode(materiel.getCode())) {
            throw new TerrainConflitException("Code matériel déjà utilisé : " + materiel.getCode());
        }
        if (materiel.getStatut() == null) {
            materiel.setStatut(StatutMateriel.EN_SERVICE);
        }
        LocalDateTime now = LocalDateTime.now();
        materiel.setCreatedAt(now);
        materiel.setUpdatedAt(now);
        return repository.save(materiel);
    }

    public Materiel update(String id, Materiel patch) {
        Materiel entity = loadOrThrow(id);
        if (patch.getCode() != null && !patch.getCode().equals(entity.getCode())
                && repository.existsByCode(patch.getCode())) {
            throw new TerrainConflitException("Code matériel déjà utilisé : " + patch.getCode());
        }
        patch.setId(entity.getId());
        patch.setCreatedAt(entity.getCreatedAt());
        patch.setUpdatedAt(LocalDateTime.now());
        return repository.save(patch);
    }

    public void delete(String id) {
        repository.delete(loadOrThrow(id));
    }

    // ───────────────────────── Événements ─────────────────────────

    public Materiel affecter(String materielId, String siteId, String commentaire) {
        Materiel materiel = loadOrThrow(materielId);
        SiteClient site = siteService.loadOrThrow(siteId);
        String siteAvant = materiel.getSiteAffecteId();

        evenementRepository.save(EvenementMateriel.builder()
                .materielId(materielId)
                .type(TypeEvenementMateriel.AFFECTATION)
                .date(LocalDateTime.now())
                .description(commentaire != null ? commentaire : "Affectation au site " + site.getNom())
                .siteAvantId(siteAvant)
                .siteApresId(siteId)
                .createdAt(LocalDateTime.now())
                .build());

        materiel.setSiteAffecteId(siteId);
        materiel.setSiteAffecteNom(site.getNom());
        materiel.setUpdatedAt(LocalDateTime.now());
        return repository.save(materiel);
    }

    public List<EvenementMateriel> historique(String materielId) {
        loadOrThrow(materielId);
        return evenementRepository.findByMaterielIdOrderByDateDesc(materielId);
    }

    public EvenementMateriel panne(String materielId, String description) {
        Materiel materiel = loadOrThrow(materielId);
        materiel.setStatut(StatutMateriel.EN_PANNE);
        materiel.setUpdatedAt(LocalDateTime.now());
        repository.save(materiel);

        return evenementRepository.save(EvenementMateriel.builder()
                .materielId(materielId)
                .type(TypeEvenementMateriel.PANNE)
                .date(LocalDateTime.now())
                .description(description)
                .resolu(false)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public EvenementMateriel maintenance(String materielId, EvenementMateriel evenement) {
        Materiel materiel = loadOrThrow(materielId);

        evenement.setId(null);
        evenement.setMaterielId(materielId);
        if (evenement.getType() == null) {
            evenement.setType(TypeEvenementMateriel.MAINTENANCE_PROGRAMMEE);
        }
        if (evenement.getDate() == null) {
            evenement.setDate(LocalDateTime.now());
        }
        evenement.setCreatedAt(LocalDateTime.now());
        EvenementMateriel saved = evenementRepository.save(evenement);

        LocalDate derniere = evenement.getDate().toLocalDate();
        materiel.setDerniereMaintenance(derniere);
        if (materiel.getIntervalleMaintenanceJours() != null) {
            materiel.setProchaineMaintenance(derniere.plusDays(materiel.getIntervalleMaintenanceJours()));
        }
        materiel.setUpdatedAt(LocalDateTime.now());
        repository.save(materiel);
        return saved;
    }

    // ───────────────────────── Maintenance programmée ─────────────────────────

    public List<MaintenanceProgrammee> listMaintenances(LocalDate dateDebut, LocalDate dateFin) {
        return maintenanceRepository.findByDateProgrammeeBetween(dateDebut, dateFin);
    }

    public MaintenanceProgrammee creerMaintenance(MaintenanceProgrammee maintenance) {
        maintenance.setId(null);
        if (maintenance.getMaterielId() != null) {
            repository.findById(maintenance.getMaterielId())
                    .ifPresent(m -> maintenance.setMaterielNom(m.getNom()));
        }
        return maintenanceRepository.save(maintenance);
    }

    // ───────────────────────── Alertes maintenance ─────────────────────────

    public List<AlerteMaintenance> alertes() {
        LocalDate today = LocalDate.now();
        List<AlerteMaintenance> alertes = new ArrayList<>();
        for (Materiel m : repository.findAll()) {
            if (m.getProchaineMaintenance() == null) continue;
            long joursRestants = ChronoUnit.DAYS.between(today, m.getProchaineMaintenance());
            if (joursRestants > 30) continue;

            NiveauAlerteMaintenance niveau;
            if (joursRestants < 0) {
                niveau = NiveauAlerteMaintenance.CRITIQUE;
            } else if (joursRestants <= 7) {
                niveau = NiveauAlerteMaintenance.ATTENTION;
            } else {
                niveau = NiveauAlerteMaintenance.INFO;
            }
            alertes.add(AlerteMaintenance.builder()
                    .materielId(m.getId())
                    .materielCode(m.getCode())
                    .materielNom(m.getNom())
                    .prochaineMaintenance(m.getProchaineMaintenance())
                    .joursRestants(joursRestants)
                    .niveau(niveau)
                    .build());
        }
        return alertes;
    }

    public Materiel loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Matériel introuvable : " + id));
    }
}