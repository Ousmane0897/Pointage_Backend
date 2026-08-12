package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AffectationAgentDto;
import com.example.Pointage_Cleanic.Dto.terrain.ConflitAffectation;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.Mapper.terrain.AffectationAgentMapper;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.TerrainConflitException;
import com.example.Pointage_Cleanic.repositories.terrain.AffectationAgentRepository;
import com.example.Pointage_Cleanic.services.terrain.ReferentielRhService.AgentRef;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlanningService {

    private static final List<StatutAffectation> STATUTS_ACTIFS =
            List.of(StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS, StatutAffectation.EFFECTUEE);

    /** Seuls ces statuts peuvent être annulés : EFFECTUEE / ANNULEE / REMPLACEE sont terminaux. */
    private static final List<StatutAffectation> STATUTS_ANNULABLES =
            List.of(StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS);

    /** Seuls ces statuts peuvent être modifiés : une prestation terminée/annulée/remplacée est figée. */
    private static final List<StatutAffectation> STATUTS_MODIFIABLES =
            List.of(StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS);

    private static final int LONGUEUR_MIN_MOTIF_ANNULATION = 5;

    private final AffectationAgentRepository repository;
    private final AffectationAgentMapper mapper;
    private final MongoTemplate mongoTemplate;
    private final ReferentielRhService referentielRh;
    private final SiteClientService siteService;
    private final CurrentUserProvider currentUser;

    // ───────────────────────── Lecture ─────────────────────────

    public PageResponse<AffectationAgentDto> list(int page, int size, LocalDate dateDebut, LocalDate dateFin,
                                                  String employeId, String siteId, StatutAffectation statut) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateDebut").descending());
        Query query = new Query().with(pageable);
        appliquerFiltres(query, dateDebut, dateFin, employeId, siteId, statut);

        List<AffectationAgent> results = mongoTemplate.find(query, AffectationAgent.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), AffectationAgent.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public List<AffectationAgentDto> listePeriode(LocalDate dateDebut, LocalDate dateFin,
                                                  String employeId, String siteId, StatutAffectation statut) {
        Query query = new Query().with(Sort.by("dateDebut").ascending());
        appliquerFiltres(query, dateDebut, dateFin, employeId, siteId, statut);
        return mongoTemplate.find(query, AffectationAgent.class).stream().map(mapper::toDto).toList();
    }

    /**
     * Compteurs d'affectations par statut, même sémantique de filtrage que {@link #list}.
     * Les 5 clés de l'enum sont toujours présentes (0 inclus) et dans l'ordre de déclaration :
     * le front alimente ses onglets à compteurs avec ce seul appel.
     */
    public Map<StatutAffectation, Long> stats(LocalDate dateDebut, LocalDate dateFin) {
        Map<StatutAffectation, Long> resultat = new LinkedHashMap<>();
        for (StatutAffectation statut : StatutAffectation.values()) {
            Query query = new Query();
            appliquerFiltres(query, dateDebut, dateFin, null, null, statut);
            resultat.put(statut, mongoTemplate.count(query, AffectationAgent.class));
        }
        return resultat;
    }

    private void appliquerFiltres(Query query, LocalDate dateDebut, LocalDate dateFin,
                                  String employeId, String siteId, StatutAffectation statut) {
        if (dateDebut != null && dateFin != null) {
            // Chevauchement avec la fenêtre [dateDebut 00:00, dateFin 23:59:59]
            query.addCriteria(new Criteria().andOperator(
                    finApres(dateDebut),
                    Criteria.where("dateDebut").lte(dateFin.atTime(LocalTime.MAX))));
        } else if (dateDebut != null) {
            query.addCriteria(finApres(dateDebut));
        } else if (dateFin != null) {
            query.addCriteria(Criteria.where("dateDebut").lte(dateFin.atTime(LocalTime.MAX)));
        }
        if (employeId != null && !employeId.isBlank()) {
            query.addCriteria(Criteria.where("employeId").is(employeId));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteId").is(siteId));
        }
        if (statut != null) {
            query.addCriteria(Criteria.where("statut").is(statut));
        }
    }

    /**
     * Borne basse d'une fenêtre : l'affectation se termine après le début de la
     * fenêtre, ou n'a pas de fin du tout (durée indéterminée) — auquel cas elle
     * chevauche toute fenêtre postérieure à son début.
     */
    private Criteria finApres(LocalDate borneBasse) {
        return new Criteria().orOperator(
                Criteria.where("dateFin").gte(borneBasse.atStartOfDay()),
                Criteria.where("dateFin").is(null));
    }

    public AffectationAgentDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public AffectationAgent loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Affectation introuvable : " + id));
    }

    // ───────────────────────── Écriture ─────────────────────────

    public AffectationAgentDto create(AffectationAgentDto dto) {
        AffectationAgent entity = mapper.toEntity(dto);
        entity.setId(null);
        validerEtDenormaliser(entity);
        if (entity.getStatut() == null) {
            entity.setStatut(StatutAffectation.PLANIFIEE);
        }
        verifierConflit(entity, null);
        appliquerRemplacement(entity);

        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return mapper.toDto(repository.save(entity));
    }

    public AffectationAgentDto update(String id, AffectationAgentDto dto) {
        AffectationAgent entity = loadOrThrow(id);
        // Garde de statut : une affectation figée (EFFECTUEE / ANNULEE / REMPLACEE) ne se modifie plus.
        // On teste le statut STOCKÉ (avant mapping du DTO), pas celui du corps de la requête.
        if (!STATUTS_MODIFIABLES.contains(entity.getStatut())) {
            throw new TerrainConflitException(
                    "Modification impossible : l'affectation est au statut " + entity.getStatut());
        }
        mapper.updateEntityFromDto(dto, entity);
        // Le mapper ignore les valeurs nulles : `dateFin` étant optionnelle, on la
        // réaffecte explicitement pour permettre le retour à une durée indéterminée.
        entity.setDateFin(dto.getDateFin());
        validerEtDenormaliser(entity);
        verifierConflit(entity, id);
        entity.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(entity));
    }

    // Pas de delete() : une affectation ne se supprime pas, elle s'annule (voir annuler()).
    // Le hard delete a été retiré le 2026-07-21 — il permettait d'effacer une ligne sans
    // laisser de trace, contournant la traçabilité exigée par le métier.

    /**
     * Annule une affectation en la conservant dans l'historique (statut {@code ANNULEE} + traçabilité).
     * Motif obligatoire, 5 caractères minimum après {@code trim} → 400 sinon ; affectation introuvable
     * → 404 ; statut terminal (EFFECTUEE / ANNULEE / REMPLACEE) → 409.
     *
     * <p>L'auteur est déduit du JWT via {@link CurrentUserProvider}, jamais du corps de la requête.
     * Rejouer l'appel sur une affectation déjà annulée retombe sur la garde de statut (409).
     *
     * <p><b>Course avec {@link AffectationStatutScheduler} — assumée, ne pas assouplir la garde.</b>
     * Le job bascule une affectation en {@code EFFECTUEE} dès que {@code dateFin} est franchie.
     * Si cela se produit entre le chargement de la page et le clic « Annuler », l'utilisateur
     * reçoit un 409 (« Cette affectation n'est plus annulable »). C'est le comportement correct :
     * on n'annule pas rétroactivement une prestation terminée. Ajouter {@code EFFECTUEE} aux
     * {@code STATUTS_ANNULABLES} pour « corriger » ce 409 rouvrirait précisément ce trou.
     */
    public AffectationAgentDto annuler(String id, String motif) {
        String motifNet = motif == null ? "" : motif.trim();
        if (motifNet.length() < LONGUEUR_MIN_MOTIF_ANNULATION) {
            throw new IllegalArgumentException(
                    "Le motif d'annulation est obligatoire (" + LONGUEUR_MIN_MOTIF_ANNULATION + " caractères minimum)");
        }

        AffectationAgent entity = loadOrThrow(id);
        if (!STATUTS_ANNULABLES.contains(entity.getStatut())) {
            throw new TerrainConflitException(
                    "Annulation impossible : l'affectation est au statut " + entity.getStatut());
        }

        LocalDateTime now = LocalDateTime.now();
        entity.setStatut(StatutAffectation.ANNULEE);
        entity.setMotifAnnulation(motifNet);
        entity.setDateAnnulation(now);
        entity.setAnnuleParNom(currentUser.currentUserNom());
        entity.setUpdatedAt(now);
        return mapper.toDto(repository.save(entity));
    }

    private void validerEtDenormaliser(AffectationAgent entity) {
        if (entity.getDateDebut() != null && entity.getDateFin() != null
                && entity.getDateFin().isBefore(entity.getDateDebut())) {
            throw new IllegalArgumentException("dateFin doit être postérieure à dateDebut");
        }
        AgentRef agent = referentielRh.denormalise(entity.getEmployeId());
        entity.setEmployeMatricule(agent.matricule());
        entity.setEmployeNom(agent.nomComplet());

        SiteClient site = siteService.loadOrThrow(entity.getSiteId());
        entity.setSiteCode(site.getCode());
        entity.setSiteNom(site.getNom());
    }

    private void appliquerRemplacement(AffectationAgent entity) {
        if (entity.getRemplaceAffectationId() != null && !entity.getRemplaceAffectationId().isBlank()) {
            AffectationAgent initiale = loadOrThrow(entity.getRemplaceAffectationId());
            initiale.setStatut(StatutAffectation.REMPLACEE);
            initiale.setUpdatedAt(LocalDateTime.now());
            repository.save(initiale);
        }
    }

    /** Refuse (409) une affectation qui chevauche un créneau actif du même agent. */
    private void verifierConflit(AffectationAgent entity, String idCourant) {
        if (entity.getDateDebut() == null || entity.getDateFin() == null) {
            return;
        }
        List<AffectationAgent> existantes = repository.findByEmployeId(entity.getEmployeId());
        for (AffectationAgent a : existantes) {
            if (a.getId().equals(idCourant)) continue;
            if (a.getId().equals(entity.getRemplaceAffectationId())) continue;
            if (!STATUTS_ACTIFS.contains(a.getStatut())) continue;
            if (chevauche(entity.getDateDebut(), entity.getDateFin(), a.getDateDebut(), a.getDateFin())) {
                throw new TerrainConflitException(
                        "Conflit de planning : l'agent " + entity.getEmployeNom()
                                + " est déjà affecté sur le créneau " + a.getDateDebut() + " → " + a.getDateFin());
            }
        }
    }

    // ───────────────────────── Conflits ─────────────────────────

    public List<ConflitAffectation> conflits(LocalDate dateDebut, LocalDate dateFin) {
        List<AffectationAgent> fenetre = repository
                .findByStatutInAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        STATUTS_ACTIFS, dateDebut.atStartOfDay(), dateFin.atTime(LocalTime.MAX));

        Map<String, List<AffectationAgent>> parAgent = new LinkedHashMap<>();
        for (AffectationAgent a : fenetre) {
            parAgent.computeIfAbsent(a.getEmployeId(), k -> new ArrayList<>()).add(a);
        }

        List<ConflitAffectation> conflits = new ArrayList<>();
        for (Map.Entry<String, List<AffectationAgent>> e : parAgent.entrySet()) {
            List<AffectationAgent> liste = e.getValue();
            if (liste.size() < 2) continue;

            List<AffectationAgent> enConflit = new ArrayList<>();
            for (int i = 0; i < liste.size(); i++) {
                for (int j = i + 1; j < liste.size(); j++) {
                    AffectationAgent a = liste.get(i);
                    AffectationAgent b = liste.get(j);
                    if (chevauche(a.getDateDebut(), a.getDateFin(), b.getDateDebut(), b.getDateFin())) {
                        if (!enConflit.contains(a)) enConflit.add(a);
                        if (!enConflit.contains(b)) enConflit.add(b);
                    }
                }
            }
            if (enConflit.isEmpty()) continue;

            LocalDateTime debutRecouvrement = enConflit.stream()
                    .map(AffectationAgent::getDateDebut).max(Comparator.naturalOrder()).orElse(null);
            LocalDateTime finRecouvrement = enConflit.stream()
                    .map(AffectationAgent::getDateFin).min(Comparator.naturalOrder()).orElse(null);

            conflits.add(ConflitAffectation.builder()
                    .employeId(e.getKey())
                    .employeNom(enConflit.get(0).getEmployeNom())
                    .affectations(enConflit.stream().map(mapper::toDto).toList())
                    .intervalleDebut(debutRecouvrement)
                    .intervalleFin(finRecouvrement)
                    .build());
        }
        return conflits;
    }

    private boolean chevauche(LocalDateTime aDebut, LocalDateTime aFin, LocalDateTime bDebut, LocalDateTime bFin) {
        if (aDebut == null || aFin == null || bDebut == null || bFin == null) return false;
        return aDebut.isBefore(bFin) && bDebut.isBefore(aFin);
    }
}