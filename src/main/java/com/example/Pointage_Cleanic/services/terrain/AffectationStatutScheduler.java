package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.repositories.terrain.AffectationAgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Bascule automatique du statut des affectations terrain selon l'heure courante.
 * <p>
 * Chaque minute, pour toute affectation dont le statut ∈ {PLANIFIEE, EN_COURS} :
 * <pre>
 *   now &gt;= dateFin   -&gt; EFFECTUEE
 *   now &gt;= dateDebut -&gt; EN_COURS
 *   sinon            -&gt; inchangé (reste PLANIFIEE)
 * </pre>
 * Bornes inclusives au début ({@code now == dateDebut} → EN_COURS,
 * {@code now == dateFin} → EFFECTUEE). {@code EFFECTUEE} est testé avant
 * {@code EN_COURS} pour gérer le rattrapage d'une affectation dont les deux dates
 * sont déjà dépassées. {@code ANNULEE}, {@code REMPLACEE} et {@code EFFECTUEE} ne
 * sont jamais modifiés. Idempotent : seules les affectations dont le statut change
 * réellement sont réécrites.
 * <p>
 * Dates comparées en {@link LocalDateTime} (cohérent avec tout le module terrain ;
 * Dakar = UTC+0 sans heure d'été). Le {@link Clock} injecté rend {@code now}
 * contrôlable en test.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AffectationStatutScheduler {

    private static final List<StatutAffectation> ACTIVES =
            List.of(StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS);

    private final AffectationAgentRepository affectationRepository;
    private final Clock clock;

    /** Seconde 0 de chaque minute (zone Africa/Dakar). */
    @Scheduled(cron = "0 * * * * *", zone = "Africa/Dakar")
    public void rafraichirStatuts() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            List<AffectationAgent> candidats = affectationRepository.findByStatutIn(ACTIVES);

            List<AffectationAgent> aSauver = new ArrayList<>();
            for (AffectationAgent aff : candidats) {
                StatutAffectation cible =
                        appliquerRegle(aff.getStatut(), aff.getDateDebut(), aff.getDateFin(), now);
                if (cible != aff.getStatut()) {
                    aff.setStatut(cible);
                    aff.setUpdatedAt(now);
                    aSauver.add(aff);
                }
            }

            if (!aSauver.isEmpty()) {
                affectationRepository.saveAll(aSauver);
            }
            log.info("Bascule statuts affectations : {} affectation(s) mise(s) à jour", aSauver.size());
        } catch (Exception e) {
            log.error("Job de bascule des statuts d'affectation en échec : {}", e.getMessage(), e);
        }
    }

    /**
     * Règle pure de transition de statut. {@code !now.isBefore(x)} ⇔ {@code now >= x}
     * (bornes inclusives au début).
     */
    static StatutAffectation appliquerRegle(StatutAffectation actuel,
                                            LocalDateTime debut, LocalDateTime fin, LocalDateTime now) {
        if (actuel != StatutAffectation.PLANIFIEE && actuel != StatutAffectation.EN_COURS) {
            return actuel;
        }
        if (fin != null && !now.isBefore(fin)) {
            return StatutAffectation.EFFECTUEE;
        }
        if (debut != null && !now.isBefore(debut)) {
            return StatutAffectation.EN_COURS;
        }
        return actuel;
    }
}
