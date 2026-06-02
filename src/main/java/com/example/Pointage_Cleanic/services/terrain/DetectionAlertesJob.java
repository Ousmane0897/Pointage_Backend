package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.NiveauEscalade;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAlerte;
import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteTerrain;
import com.example.Pointage_Cleanic.Enum.terrain.TypePointage;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.entities.terrain.AlerteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.ParametresEscalade;
import com.example.Pointage_Cleanic.entities.terrain.PointageTerrain;
import com.example.Pointage_Cleanic.repositories.terrain.AffectationAgentRepository;
import com.example.Pointage_Cleanic.repositories.terrain.AlerteTerrainRepository;
import com.example.Pointage_Cleanic.repositories.terrain.PointageTerrainRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Détection planifiée des alertes terrain (RETARD / ABSENCE / DEPART_PREMATURE) et
 * moteur d'escalade temporelle, sur le périmètre du département Exploitation
 * (les affectations ne concernent que des agents Exploitation, validés à la création).
 * <p>
 * Hypothèse d'escalade : délais cumulés depuis {@code dateDetection} — escalade vers
 * RESPONSABLE_OPERATIONNEL à {@code delaiEscaladeNiveau1Minutes}, puis vers DIRECTION_GENERALE
 * à {@code delaiEscaladeNiveau1Minutes + delaiEscaladeNiveau2Minutes}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DetectionAlertesJob {

    private static final List<StatutAlerte> ESCALADABLES =
            List.of(StatutAlerte.OUVERTE, StatutAlerte.NOTIFIEE, StatutAlerte.ESCALADEE);

    private final AffectationAgentRepository affectationRepository;
    private final PointageTerrainRepository pointageRepository;
    private final AlerteTerrainRepository alerteRepository;
    private final AlerteService alerteService;

    /** Toutes les 5 minutes (zone Africa/Dakar). */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void detecter() {
        try {
            ParametresEscalade params = alerteService.getParametresEntity();
            LocalDateTime now = LocalDateTime.now();

            detecterRetardsEtAbsences(params, now);
            detecterDepartsPrematures(now);
            escalader(params, now);
        } catch (Exception e) {
            log.error("Job de détection des alertes terrain en échec : {}", e.getMessage(), e);
        }
    }

    private void detecterRetardsEtAbsences(ParametresEscalade params, LocalDateTime now) {
        List<AffectationAgent> affectations = affectationRepository
                .findByStatutInAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        List.of(StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS),
                        now.minusDays(1), now);

        for (AffectationAgent aff : affectations) {
            if (aff.getDateDebut() == null) continue;

            boolean aPointeEntree = pointagesJour(aff).stream()
                    .anyMatch(p -> p.getType() == TypePointage.ENTREE);
            if (aPointeEntree) continue;

            long minutesEcoulees = java.time.Duration.between(aff.getDateDebut(), now).toMinutes();

            if (minutesEcoulees >= params.getDelaiAbsenceMinutes()) {
                creerSiAbsente(TypeAlerteTerrain.ABSENCE, aff);
            } else if (minutesEcoulees >= params.getDelaiRetardMinutes()) {
                creerSiAbsente(TypeAlerteTerrain.RETARD, aff);
            }
        }
    }

    private void detecterDepartsPrematures(LocalDateTime now) {
        List<AffectationAgent> affectations = affectationRepository
                .findByStatutInAndDateFinGreaterThanEqualAndDateDebutLessThanEqual(
                        List.of(StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS, StatutAffectation.EFFECTUEE),
                        now.minusDays(1), now);

        for (AffectationAgent aff : affectations) {
            if (aff.getDateFin() == null) continue;
            pointagesJour(aff).stream()
                    .filter(p -> p.getType() == TypePointage.SORTIE)
                    .filter(p -> p.getDatePointage() != null && p.getDatePointage().isBefore(aff.getDateFin()))
                    .findFirst()
                    .ifPresent(sortie -> creerSiAbsente(TypeAlerteTerrain.DEPART_PREMATURE, aff, sortie));
        }
    }

    private void escalader(ParametresEscalade params, LocalDateTime now) {
        List<AlerteTerrain> alertes = alerteRepository.findByStatutIn(ESCALADABLES);
        long seuilN1 = params.getDelaiEscaladeNiveau1Minutes();
        long seuilN2 = seuilN1 + params.getDelaiEscaladeNiveau2Minutes();

        for (AlerteTerrain a : alertes) {
            if (a.getDateDetection() == null) continue;
            long minutes = java.time.Duration.between(a.getDateDetection(), now).toMinutes();

            if (a.getNiveauActuel() == NiveauEscalade.SUPERVISEUR && minutes >= seuilN1) {
                alerteService.escaladeAuNiveauSuivant(a, "Escalade automatique (délai niveau 1 dépassé)");
                alerteRepository.save(a);
            } else if (a.getNiveauActuel() == NiveauEscalade.RESPONSABLE_OPERATIONNEL && minutes >= seuilN2) {
                alerteService.escaladeAuNiveauSuivant(a, "Escalade automatique (délai niveau 2 dépassé)");
                alerteRepository.save(a);
            }
        }
    }

    private List<PointageTerrain> pointagesJour(AffectationAgent aff) {
        LocalDateTime debut = aff.getDateDebut().toLocalDate().atStartOfDay();
        LocalDateTime fin = aff.getDateFin() == null ? debut.plusDays(1) : aff.getDateFin();
        return pointageRepository.findByEmployeIdAndDatePointageBetween(aff.getEmployeId(), debut, fin);
    }

    private boolean existeDeja(TypeAlerteTerrain type, AffectationAgent aff) {
        return !alerteRepository
                .findByTypeAndEmployeIdAndAffectationId(type, aff.getEmployeId(), aff.getId())
                .isEmpty();
    }

    private void creerSiAbsente(TypeAlerteTerrain type, AffectationAgent aff) {
        creerSiAbsente(type, aff, null);
    }

    private void creerSiAbsente(TypeAlerteTerrain type, AffectationAgent aff, PointageTerrain pointage) {
        if (existeDeja(type, aff)) return;
        LocalDateTime dateEvenement = (type == TypeAlerteTerrain.DEPART_PREMATURE && pointage != null)
                ? pointage.getDatePointage() : aff.getDateDebut();
        alerteService.creerAlerte(type,
                aff.getEmployeId(), aff.getEmployeMatricule(), aff.getEmployeNom(),
                aff.getSiteId(), aff.getSiteNom(),
                aff.getId(), pointage == null ? null : pointage.getId(), dateEvenement);
    }
}