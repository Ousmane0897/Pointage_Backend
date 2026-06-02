package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AlerteTerrainDto;
import com.example.Pointage_Cleanic.Dto.terrain.NotificationTerrain;
import com.example.Pointage_Cleanic.Dto.terrain.ParametresEscaladeDto;
import com.example.Pointage_Cleanic.Dto.terrain.RecapitulatifQuotidien;
import com.example.Pointage_Cleanic.Enum.terrain.NiveauEscalade;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAlerte;
import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteTerrain;
import com.example.Pointage_Cleanic.Mapper.terrain.AlerteTerrainMapper;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.entities.terrain.AlerteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.EscaladeEntry;
import com.example.Pointage_Cleanic.entities.terrain.ParametresEscalade;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.repositories.terrain.AffectationAgentRepository;
import com.example.Pointage_Cleanic.repositories.terrain.AlerteTerrainRepository;
import com.example.Pointage_Cleanic.repositories.terrain.ParametresEscaladeRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlerteService {

    private final AlerteTerrainRepository repository;
    private final ParametresEscaladeRepository parametresRepository;
    private final AffectationAgentRepository affectationRepository;
    private final AlerteTerrainMapper mapper;
    private final CurrentUserProvider currentUser;
    private final TerrainNotificationService notification;
    private final UtilisateurRepository utilisateurRepository;
    private final MongoTemplate mongoTemplate;

    // ───────────────────────── Lecture ─────────────────────────

    public PageResponse<AlerteTerrainDto> list(int page, int size, LocalDate dateDebut, LocalDate dateFin,
                                               TypeAlerteTerrain type, StatutAlerte statut,
                                               NiveauEscalade niveauActuel, String employeId, String siteId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateDetection").descending());
        Query query = new Query().with(pageable);
        if (dateDebut != null) {
            query.addCriteria(Criteria.where("dateEvenement").gte(dateDebut.atStartOfDay()));
        }
        if (dateFin != null) {
            query.addCriteria(Criteria.where("dateEvenement").lte(dateFin.atTime(LocalTime.MAX)));
        }
        if (type != null) query.addCriteria(Criteria.where("type").is(type));
        if (statut != null) query.addCriteria(Criteria.where("statut").is(statut));
        if (niveauActuel != null) query.addCriteria(Criteria.where("niveauActuel").is(niveauActuel));
        if (employeId != null && !employeId.isBlank()) query.addCriteria(Criteria.where("employeId").is(employeId));
        if (siteId != null && !siteId.isBlank()) query.addCriteria(Criteria.where("siteId").is(siteId));

        List<AlerteTerrain> results = mongoTemplate.find(query, AlerteTerrain.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), AlerteTerrain.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public List<AlerteTerrainDto> courantes() {
        return repository.findByStatutIn(
                        List.of(StatutAlerte.OUVERTE, StatutAlerte.NOTIFIEE, StatutAlerte.ESCALADEE))
                .stream().map(mapper::toDto).toList();
    }

    public AlerteTerrainDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public AlerteTerrain loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerte introuvable : " + id));
    }

    // ───────────────────────── Résolution ─────────────────────────

    public AlerteTerrainDto traiter(String id, String commentaire) {
        AlerteTerrain a = loadOrThrow(id);
        a.setStatut(StatutAlerte.TRAITEE);
        a.setCommentaire(commentaire);
        a.setResoluParId(currentUser.currentUserId());
        a.setResoluParNom(currentUser.currentUserNom());
        a.setDateResolution(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        AlerteTerrain saved = repository.save(a);
        notifierResolution(saved, "Alerte traitée");
        return mapper.toDto(saved);
    }

    public AlerteTerrainDto justifier(String id, String motif) {
        AlerteTerrain a = loadOrThrow(id);
        a.setStatut(StatutAlerte.JUSTIFIEE);
        a.setCommentaire(motif);
        a.setResoluParId(currentUser.currentUserId());
        a.setResoluParNom(currentUser.currentUserNom());
        a.setDateResolution(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        AlerteTerrain saved = repository.save(a);
        notifierResolution(saved, "Alerte justifiée");
        return mapper.toDto(saved);
    }

    public AlerteTerrainDto escalader(String id, String motif) {
        AlerteTerrain a = loadOrThrow(id);
        escaladeAuNiveauSuivant(a, motif);
        return mapper.toDto(repository.save(a));
    }

    // ───────────────────────── Création (pointage / job) ─────────────────────────

    public AlerteTerrain creerAlerte(TypeAlerteTerrain type, String employeId, String employeMatricule,
                                     String employeNom, String siteId, String siteNom,
                                     String affectationId, String pointageId, LocalDateTime dateEvenement) {
        ParametresEscalade params = getParametresEntity();
        String destinataireId = premier(params.getSuperviseursIds());
        LocalDateTime now = LocalDateTime.now();

        AlerteTerrain alerte = AlerteTerrain.builder()
                .type(type)
                .niveauActuel(NiveauEscalade.SUPERVISEUR)
                .statut(StatutAlerte.OUVERTE)
                .employeId(employeId)
                .employeMatricule(employeMatricule)
                .employeNom(employeNom)
                .siteId(siteId)
                .siteNom(siteNom)
                .affectationId(affectationId)
                .pointageId(pointageId)
                .dateEvenement(dateEvenement)
                .dateDetection(now)
                .destinataireActuelId(destinataireId)
                .destinataireActuelNom(resoudreNom(destinataireId))
                .createdAt(now)
                .updatedAt(now)
                .build();
        alerte.getHistoriqueEscalade().add(EscaladeEntry.builder()
                .niveau(NiveauEscalade.SUPERVISEUR)
                .destinataireId(destinataireId)
                .destinataireNom(resoudreNom(destinataireId))
                .dateEscalade(now)
                .motif("Création de l'alerte")
                .build());

        AlerteTerrain saved = repository.save(alerte);
        AlerteTerrainDto dto = mapper.toDto(saved);
        notification.diffuserAlerte(dto);
        notification.notifierDestinataire(destinataireId, NotificationTerrain.builder()
                .type(NotificationTerrain.Type.ALERTE_OUVERTE)
                .titre("Nouvelle alerte : " + type)
                .message(employeNom + " — " + (siteNom == null ? "" : siteNom))
                .alerteId(saved.getId())
                .niveau(NiveauEscalade.SUPERVISEUR.name())
                .dateEmission(now.toString())
                .build());
        return saved;
    }

    /** Escalade l'alerte au niveau supérieur (mutation en place, sans save). */
    public void escaladeAuNiveauSuivant(AlerteTerrain a, String motif) {
        NiveauEscalade suivant = niveauSuivant(a.getNiveauActuel());
        ParametresEscalade params = getParametresEntity();
        String destinataireId = destinatairePourNiveau(params, suivant);
        LocalDateTime now = LocalDateTime.now();

        a.setNiveauActuel(suivant);
        a.setStatut(StatutAlerte.ESCALADEE);
        a.setDestinataireActuelId(destinataireId);
        a.setDestinataireActuelNom(resoudreNom(destinataireId));
        a.getHistoriqueEscalade().add(EscaladeEntry.builder()
                .niveau(suivant)
                .destinataireId(destinataireId)
                .destinataireNom(resoudreNom(destinataireId))
                .dateEscalade(now)
                .motif(motif)
                .build());
        a.setUpdatedAt(now);

        AlerteTerrainDto dto = mapper.toDto(a);
        notification.diffuserAlerte(dto);
        notification.notifierDestinataire(destinataireId, NotificationTerrain.builder()
                .type(NotificationTerrain.Type.ALERTE_ESCALADEE)
                .titre("Alerte escaladée : " + a.getType())
                .message(a.getEmployeNom() + " — niveau " + suivant)
                .alerteId(a.getId())
                .niveau(suivant.name())
                .dateEmission(now.toString())
                .build());
    }

    // ───────────────────────── Récapitulatif ─────────────────────────

    public RecapitulatifQuotidien recapQuotidien(LocalDate date) {
        LocalDate jour = date == null ? LocalDate.now() : date;
        LocalDateTime debut = jour.atStartOfDay();
        LocalDateTime fin = jour.atTime(LocalTime.MAX);

        List<AlerteTerrain> alertes = repository.findByDateEvenementBetween(debut, fin);
        List<AffectationAgent> affectations = affectationRepository
                .findByStatutInAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        List.of(StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS, StatutAffectation.EFFECTUEE),
                        debut, fin);

        int nbAgents = (int) affectations.stream().map(AffectationAgent::getEmployeId).distinct().count();

        return RecapitulatifQuotidien.builder()
                .date(jour)
                .nbAgentsExploitation(nbAgents)
                .nbAffectations(affectations.size())
                .nbRetards((int) alertes.stream().filter(a -> a.getType() == TypeAlerteTerrain.RETARD).count())
                .nbAbsences((int) alertes.stream().filter(a -> a.getType() == TypeAlerteTerrain.ABSENCE).count())
                .nbPointagesHorsZone((int) alertes.stream()
                        .filter(a -> a.getType() == TypeAlerteTerrain.POINTAGE_HORS_ZONE).count())
                .alertes(alertes.stream().map(mapper::toDto).collect(Collectors.toList()))
                .build();
    }

    // ───────────────────────── Paramètres (singleton) ─────────────────────────

    public ParametresEscaladeDto getParametres() {
        return mapper.toDto(getParametresEntity());
    }

    public ParametresEscaladeDto updateParametres(ParametresEscaladeDto dto) {
        ParametresEscalade entity = getParametresEntity();
        mapper.updateEntityFromDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(currentUser.currentUserId());
        return mapper.toDto(parametresRepository.save(entity));
    }

    public ParametresEscalade getParametresEntity() {
        return parametresRepository.findAll().stream().findFirst()
                .orElseGet(() -> parametresRepository.save(ParametresEscalade.builder()
                        .delaiRetardMinutes(15)
                        .delaiAbsenceMinutes(60)
                        .delaiEscaladeNiveau1Minutes(30)
                        .delaiEscaladeNiveau2Minutes(120)
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    // ───────────────────────── Helpers ─────────────────────────

    private void notifierResolution(AlerteTerrain a, String titre) {
        AlerteTerrainDto dto = mapper.toDto(a);
        notification.diffuserAlerte(dto);
        notification.notifierDestinataire(a.getDestinataireActuelId(), NotificationTerrain.builder()
                .type(NotificationTerrain.Type.ALERTE_TRAITEE)
                .titre(titre)
                .message(a.getEmployeNom() + " — " + a.getType())
                .alerteId(a.getId())
                .niveau(a.getNiveauActuel() == null ? null : a.getNiveauActuel().name())
                .dateEmission(LocalDateTime.now().toString())
                .build());
    }

    private NiveauEscalade niveauSuivant(NiveauEscalade courant) {
        return switch (courant) {
            case SUPERVISEUR -> NiveauEscalade.RESPONSABLE_OPERATIONNEL;
            case RESPONSABLE_OPERATIONNEL -> NiveauEscalade.DIRECTION_GENERALE;
            case DIRECTION_GENERALE -> NiveauEscalade.DIRECTION_GENERALE;
        };
    }

    private String destinatairePourNiveau(ParametresEscalade p, NiveauEscalade niveau) {
        return switch (niveau) {
            case SUPERVISEUR -> premier(p.getSuperviseursIds());
            case RESPONSABLE_OPERATIONNEL -> premier(p.getResponsablesOperationnelsIds());
            case DIRECTION_GENERALE -> premier(p.getDirectionGeneraleIds());
        };
    }

    private String premier(List<String> ids) {
        return (ids == null || ids.isEmpty()) ? null : ids.get(0);
    }

    private String resoudreNom(String utilisateurId) {
        if (utilisateurId == null) return null;
        return utilisateurRepository.findById(utilisateurId)
                .map(this::nomComplet)
                .orElse(null);
    }

    private String nomComplet(Utilisateur u) {
        String prenom = u.getPrenom() == null ? "" : u.getPrenom().trim();
        String nom = u.getNom() == null ? "" : u.getNom().trim();
        String complet = (prenom + " " + nom).trim();
        return complet.isBlank() ? u.getEmail() : complet;
    }
}