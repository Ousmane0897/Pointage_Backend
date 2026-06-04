package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.PeriodeEssaiDto;
import com.example.Pointage_Cleanic.Dto.rh.ProlongerPeriodeEssaiRequest;
import com.example.Pointage_Cleanic.Enum.rh.StatutContrat;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.rh.StatutPeriodeEssai;
import com.example.Pointage_Cleanic.Mapper.rh.PeriodeEssaiMapper;
import com.example.Pointage_Cleanic.entities.rh.AlertePeriodeEssai;
import com.example.Pointage_Cleanic.entities.rh.Contrat;
import com.example.Pointage_Cleanic.entities.rh.DecisionPeriodeEssai;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.PeriodeEssai;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.ContratRepository;
import com.example.Pointage_Cleanic.repositories.rh.PeriodeEssaiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PeriodeEssaiService {

    static final int[] SEUILS_ALERTE_DEFAUT = {30, 15, 7};

    private final PeriodeEssaiRepository repository;
    private final PeriodeEssaiMapper mapper;
    private final ContratRepository contratRepository;

    /**
     * Résultat du seed d'une PeriodeEssai depuis un DossierEmploye.
     * {@code created} distingue une création réelle (true) d'un retour
     * idempotent sur une période active déjà existante (false).
     */
    public record SeedResult(PeriodeEssai periode, boolean created) {
        public static SeedResult skipped() {
            return new SeedResult(null, false);
        }
    }

    public Page<PeriodeEssaiDto> list(int page, int size, String statutStr) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateFin").ascending());
        Page<PeriodeEssai> result;
        if (statutStr != null && !statutStr.isBlank()) {
            StatutPeriodeEssai statut;
            try {
                statut = StatutPeriodeEssai.valueOf(statutStr);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Statut période d'essai invalide : " + statutStr);
            }
            result = repository.findByStatut(statut, pageable);
        } else {
            result = repository.findAll(pageable);
        }
        return result.map(mapper::toDto);
    }

    public PeriodeEssaiDto getById(String id) {
        return mapper.toDto(requireById(id));
    }

    public List<PeriodeEssaiDto> getAlertes() {
        LocalDate seuil = LocalDate.now().plusDays(30);
        return repository.findByStatutInAndDateFinLessThanEqualOrderByDateFinAsc(
                        List.of(StatutPeriodeEssai.EN_COURS, StatutPeriodeEssai.PROLONGE), seuil)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    public PeriodeEssaiDto prolonger(String id, ProlongerPeriodeEssaiRequest request, String decideurNom) {
        PeriodeEssai periode = requireById(id);

        if (periode.getStatut() != StatutPeriodeEssai.EN_COURS
                && periode.getStatut() != StatutPeriodeEssai.PROLONGE) {
            throw new IllegalArgumentException(
                    "Impossible de prolonger une période en statut " + periode.getStatut());
        }
        if (request == null || request.nouvelleDateFin() == null) {
            throw new IllegalArgumentException("nouvelleDateFin est obligatoire");
        }
        if (periode.getDateFin() != null && !request.nouvelleDateFin().isAfter(periode.getDateFin())) {
            throw new IllegalArgumentException(
                    "nouvelleDateFin doit être strictement postérieure à la date de fin actuelle");
        }

        periode.setDateFin(request.nouvelleDateFin());
        if (periode.getDateDebut() != null) {
            periode.setDureeJours((int) ChronoUnit.DAYS.between(periode.getDateDebut(), request.nouvelleDateFin()));
        }
        periode.setStatut(StatutPeriodeEssai.PROLONGE);
        periode.setAlertes(buildAlertesParDefaut(periode.getId(), request.nouvelleDateFin()));

        DecisionPeriodeEssai decision = DecisionPeriodeEssai.builder()
                .id(UUID.randomUUID().toString())
                .periodeEssaiId(periode.getId())
                .decision(StatutPeriodeEssai.PROLONGE)
                .dateDecision(LocalDate.now())
                .decideurNom(decideurNom)
                .decideurRole("RH")
                .commentaire(request.commentaire())
                .build();
        if (periode.getDecisions() == null) {
            periode.setDecisions(new ArrayList<>());
        }
        periode.getDecisions().add(decision);

        return mapper.toDto(repository.save(periode));
    }

    /**
     * Crée une PeriodeEssai à partir d'un Contrat en convertissant la durée
     * exprimée en mois (convention RH) en jours calendaires via
     * {@code dateDebut.plusMonths} (3 mois ≠ 90 jours fixes selon les mois
     * traversés).
     */
    public PeriodeEssai seedFromContrat(Contrat contrat, int dureeEssaiMois) {
        if (contrat.getDateDebut() == null) {
            throw new IllegalArgumentException(
                    "Le contrat doit avoir une date de début pour générer la période d'essai");
        }
        if (dureeEssaiMois <= 0) {
            throw new IllegalArgumentException("dureeEssaiMois doit être > 0");
        }

        // Idempotence par employé : si une période active existe déjà, on la
        // retourne sans rien créer — couvre le cas où DossierEmploye a déjà
        // déclenché le seed avant la création du contrat.
        if (contrat.getEmployeId() != null) {
            Optional<PeriodeEssai> active = repository.findFirstByEmployeIdAndStatutIn(
                    contrat.getEmployeId(),
                    List.of(StatutPeriodeEssai.EN_COURS, StatutPeriodeEssai.PROLONGE));
            if (active.isPresent()) {
                return active.get();
            }
        }

        LocalDate dateDebut = contrat.getDateDebut();
        LocalDate dateFin = dateDebut.plusMonths(dureeEssaiMois);
        int dureeJours = (int) ChronoUnit.DAYS.between(dateDebut, dateFin);

        PeriodeEssai periode = PeriodeEssai.builder()
                .employeId(contrat.getEmployeId())
                .employeNom(contrat.getEmployeNom())
                .employePrenom(contrat.getEmployePrenom())
                .contratId(contrat.getId())
                .typeContrat(contrat.getTypeContrat())
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .dureeJours(dureeJours)
                .statut(StatutPeriodeEssai.EN_COURS)
                .alertes(new ArrayList<>())
                .decisions(new ArrayList<>())
                .build();

        PeriodeEssai saved = repository.save(periode);
        saved.setAlertes(buildAlertesParDefaut(saved.getId(), dateFin));
        return repository.save(saved);
    }

    /**
     * Crée une PeriodeEssai à partir d'un DossierEmploye en statut
     * EN_PERIODE_ESSAI. Source de vérité indépendante du Contrat — utilisée
     * par {@code DossierEmployeService} dans tous les chemins de save
     * (create / update / updateStatut / titulariser / importBulk) et par le
     * runner de backfill au démarrage.
     * <p>
     * Idempotent : si une période active (EN_COURS ou PROLONGE) existe déjà
     * pour l'employé, elle est retournée sans modification ({@code created=false}).
     * <p>
     * Si l'employé porte un Contrat ACTIF, ses {@code id} et {@code typeContrat}
     * sont dénormalisés sur la PeriodeEssai. Sinon {@code contratId=""} et
     * {@code typeContrat=null}, conformément au contrat frontend qui accepte
     * un employé en période d'essai sans contrat encore enregistré.
     */
    public SeedResult seedFromDossier(DossierEmploye dossier) {
        if (dossier == null || dossier.getStatut() != StatutDossierEmploye.EN_PERIODE_ESSAI) {
            return SeedResult.skipped();
        }
        if (dossier.getDateEntree() == null) {
            return SeedResult.skipped();
        }
        if (dossier.getDureeEssaiMois() == null || dossier.getDureeEssaiMois() <= 0) {
            return SeedResult.skipped();
        }

        Optional<PeriodeEssai> existing = repository.findFirstByEmployeIdAndStatutIn(
                dossier.getId(),
                List.of(StatutPeriodeEssai.EN_COURS, StatutPeriodeEssai.PROLONGE));
        if (existing.isPresent()) {
            return new SeedResult(existing.get(), false);
        }

        LocalDate dateDebut = dossier.getDateEntree();
        LocalDate dateFin = dateDebut.plusMonths(dossier.getDureeEssaiMois());
        int dureeJours = (int) ChronoUnit.DAYS.between(dateDebut, dateFin);

        Contrat contratActif = trouverContratActif(dossier.getId());
        String contratId = (contratActif != null && contratActif.getId() != null)
                ? contratActif.getId() : "";

        PeriodeEssai periode = PeriodeEssai.builder()
                .employeId(dossier.getId())
                .employeNom(dossier.getNom())
                .employePrenom(dossier.getPrenom())
                .contratId(contratId)
                .typeContrat(contratActif != null ? contratActif.getTypeContrat() : null)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .dureeJours(dureeJours)
                .statut(StatutPeriodeEssai.EN_COURS)
                .alertes(new ArrayList<>())
                .decisions(new ArrayList<>())
                .build();

        PeriodeEssai saved = repository.save(periode);
        saved.setAlertes(buildAlertesParDefaut(saved.getId(), dateFin));
        return new SeedResult(repository.save(saved), true);
    }

    private Contrat trouverContratActif(String employeId) {
        if (employeId == null || employeId.isBlank()) return null;
        return contratRepository.findByEmployeId(employeId).stream()
                .filter(c -> c.getStatut() == StatutContrat.ACTIF)
                .findFirst()
                .orElse(null);
    }

    /**
     * Applique l'effet TITULARISE sur la période. Idempotent si déjà titularisée.
     * Appelée par DemandeValidationPeriodeEssaiService à l'étape CONFIRMEE.
     */
    public PeriodeEssai applyTitularisation(String id, String decideurNom, String commentaire) {
        PeriodeEssai periode = requireById(id);
        if (periode.getStatut() == StatutPeriodeEssai.TITULARISE) {
            return periode;
        }
        periode.setStatut(StatutPeriodeEssai.TITULARISE);
        DecisionPeriodeEssai decision = DecisionPeriodeEssai.builder()
                .id(UUID.randomUUID().toString())
                .periodeEssaiId(periode.getId())
                .decision(StatutPeriodeEssai.TITULARISE)
                .dateDecision(LocalDate.now())
                .decideurNom(decideurNom)
                .decideurRole("RH")
                .commentaire(commentaire)
                .build();
        if (periode.getDecisions() == null) {
            periode.setDecisions(new ArrayList<>());
        }
        periode.getDecisions().add(decision);
        return repository.save(periode);
    }

    /**
     * Auto-clôture la période active d'un employé qui sort de EN_PERIODE_ESSAI
     * via l'API DossierEmploye (transition manuelle, hors workflow officiel).
     * <p>
     * Cible {@code TITULARISE} pour un retour ACTIF, {@code NON_RENOUVELE} pour
     * SUSPENDU/SORTI. Idempotent : retour vide si aucune période active n'existe
     * pour l'employé, ou si la cible coïncide déjà avec le statut courant.
     */
    public Optional<PeriodeEssai> applyTransitionStatutForEmploye(
            String employeId, StatutPeriodeEssai cible, String decideurNom, String commentaire) {
        Optional<PeriodeEssai> active = repository.findFirstByEmployeIdAndStatutIn(
                employeId,
                List.of(StatutPeriodeEssai.EN_COURS, StatutPeriodeEssai.PROLONGE));
        if (active.isEmpty()) {
            return Optional.empty();
        }
        PeriodeEssai periode = active.get();
        if (periode.getStatut() == cible) {
            return Optional.of(periode);
        }
        periode.setStatut(cible);
        DecisionPeriodeEssai decision = DecisionPeriodeEssai.builder()
                .id(UUID.randomUUID().toString())
                .periodeEssaiId(periode.getId())
                .decision(cible)
                .dateDecision(LocalDate.now())
                .decideurNom(decideurNom)
                .decideurRole("RH")
                .commentaire(commentaire)
                .build();
        if (periode.getDecisions() == null) {
            periode.setDecisions(new ArrayList<>());
        }
        periode.getDecisions().add(decision);
        return Optional.of(repository.save(periode));
    }

    public PeriodeEssai requireById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Période d'essai introuvable : " + id));
    }

    private List<AlertePeriodeEssai> buildAlertesParDefaut(String periodeId, LocalDate dateFin) {
        List<AlertePeriodeEssai> alertes = new ArrayList<>();
        for (int seuil : SEUILS_ALERTE_DEFAUT) {
            alertes.add(AlertePeriodeEssai.builder()
                    .id(UUID.randomUUID().toString())
                    .periodeEssaiId(periodeId)
                    .joursAvant(seuil)
                    .dateAlerte(dateFin.minusDays(seuil))
                    .envoyee(false)
                    .build());
        }
        return alertes;
    }
}