package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Bascule automatique du statut des affectations terrain selon l'heure courante.
 * <p>
 * La colonne {@code statut} est la seule vérité : la vue à onglets du planning
 * (Planifiées / En cours / Effectuées / Annulées / Remplacées) filtre et compte
 * dessus côté serveur. Ce job la maintient alignée sur la réalité temporelle du
 * créneau — aucun calcul à la volée ailleurs.
 * <p>
 * Deux transitions seulement, appliquées <b>dans cet ordre</b> :
 * <pre>
 *   PLANIFIEE + dateDebut &lt;= now  -&gt; EN_COURS
 *   EN_COURS  + dateFin   &lt;= now  -&gt; EFFECTUEE
 * </pre>
 * L'ordre est significatif : une affectation dont le créneau est <b>entièrement
 * passé</b> (créée en retard, ou job interrompu plusieurs heures) est promue
 * {@code EN_COURS} par la première requête puis captée par la seconde, et atteint
 * donc {@code EFFECTUEE} en <b>une seule passe</b>.
 * <p>
 * <b>Statuts terminaux.</b> {@code ANNULEE} et {@code REMPLACEE} sont des décisions
 * humaines, {@code EFFECTUEE} est un état final : aucun n'est source d'une
 * transition, donc aucun n'est jamais matché. La protection découle de la clause
 * {@code statut = source} — il n'y a pas de liste d'exclusion à maintenir.
 * <p>
 * <b>Idempotence et concurrence.</b> Les mises à jour sont ensemblistes
 * ({@code updateMulti}, jamais de chargement en mémoire suivi de {@code save}) et
 * conditionnées sur le statut source. Rejouer le job n'a donc aucun effet, et deux
 * instances qui l'exécutent simultanément ne peuvent pas se doubler : la seconde
 * matche 0 document. Pas besoin de verrou distribué (ShedLock) à ce titre.
 * <p>
 * <b>Articulation avec l'annulation.</b> {@code PlanningService.annuler} n'autorise
 * que {@code PLANIFIEE} / {@code EN_COURS} et renvoie 409 sinon. Si ce job bascule
 * une affectation en {@code EFFECTUEE} entre l'affichage de la page et le clic
 * « Annuler », l'utilisateur reçoit un 409 : c'est le comportement voulu, on
 * n'annule pas rétroactivement une prestation terminée.
 * <p>
 * Dates comparées en {@link LocalDateTime}, cohérent avec tout le module terrain.
 * Le {@link Clock} injecté est ancré sur {@code Africa/Dakar} (voir
 * {@code TimeConfig}) et rend {@code now} contrôlable en test.
 *
 * @see #conditionPassageEffectuee(LocalDateTime) point d'extension si {@code EFFECTUEE}
 *      doit un jour signifier « l'agent a pointé sa sortie »
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AffectationStatutScheduler {

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    /** Seconde 0 de chaque minute (zone Africa/Dakar). */
    @Scheduled(cron = "0 * * * * *", zone = "Africa/Dakar")
    public void rafraichirStatuts() {
        try {
            LocalDateTime now = LocalDateTime.now(clock);

            // L'ordre compte : voir le javadoc de classe (rattrapage en une seule passe).
            long versEnCours = transitionner(
                    StatutAffectation.PLANIFIEE, StatutAffectation.EN_COURS,
                    conditionPassageEnCours(now), now);
            long versEffectuee = transitionner(
                    StatutAffectation.EN_COURS, StatutAffectation.EFFECTUEE,
                    conditionPassageEffectuee(now), now);

            log.info("Transitions affectations : {} → EN_COURS, {} → EFFECTUEE",
                    versEnCours, versEffectuee);
        } catch (Exception e) {
            log.error("Job de bascule des statuts d'affectation en échec : {}", e.getMessage(), e);
        }
    }

    /**
     * Rattrapage au démarrage : régularise le stock d'affectations historiques déjà
     * périmées (premier déploiement, ou arrêt prolongé de l'application).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void rattrapageAuDemarrage() {
        log.info("Rattrapage des statuts d'affectation au démarrage");
        rafraichirStatuts();
    }

    /** Le créneau a commencé. */
    private static Criteria conditionPassageEnCours(LocalDateTime now) {
        return Criteria.where("dateDebut").lte(now);
    }

    /**
     * Le créneau est écoulé.
     * <p>
     * <b>Sémantique actuelle : pure horloge.</b> {@code EFFECTUEE} signifie « le
     * créneau est passé », sans aucun lien avec le travail réellement accompli — le
     * module Pointage remonte pourtant les arrivées/départs GPS des agents.
     * <p>
     * <b>Seul point à changer</b> si le métier redéfinit {@code EFFECTUEE} comme
     * « l'agent a pointé sa sortie ». Il faudra alors décider du sort des créneaux
     * passés <b>sans</b> pointage : ils devront basculer vers un traitement d'absence
     * plutôt que vers {@code EFFECTUEE}.
     */
    private static Criteria conditionPassageEffectuee(LocalDateTime now) {
        return Criteria.where("dateFin").lte(now);
    }

    /**
     * Applique une transition en une seule écriture ensembliste.
     *
     * @return le nombre d'affectations effectivement basculées
     */
    private long transitionner(StatutAffectation source, StatutAffectation cible,
                               Criteria condition, LocalDateTime now) {
        Query query = new Query(Criteria.where("statut").is(source).andOperator(condition));
        Update update = Update.update("statut", cible).set("updatedAt", now);
        return mongoTemplate.updateMulti(query, update, AffectationAgent.class).getModifiedCount();
    }
}
