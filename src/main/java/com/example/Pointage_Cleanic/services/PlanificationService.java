package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.AnnulationDecisionMessage;
import com.example.Pointage_Cleanic.Dto.AnnulationRequestMessage;
import com.example.Pointage_Cleanic.Dto.CancelRequestDto;
import com.example.Pointage_Cleanic.Dto.PlanificationDto;
import com.example.Pointage_Cleanic.Mapper.PlanificationMapper;
import com.example.Pointage_Cleanic.entities.Agence;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.repositories.PlanificationRepository;
import java.time.Clock;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanificationService {


    private final PlanificationRepository repository;
    private final TaskScheduler taskScheduler;
    private final EmployeServices employeServices;
    private final AgencesServices agencesServices;
    private final EmployeRepository employeRepository;
    private final AgencesRepository agencesRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;




    // ✅ Spring fournit un TaskScheduler (basé sur ScheduledExecutorService)
    // qui permet de planifier des tâches à exécuter à une date précise.

    // ✅ Ces deux Maps conservent les "ScheduledFuture" (les tâches planifiées en mémoire)
    // pour pouvoir les annuler si la planification change ou est supprimée.
    private final Map<String, ScheduledFuture<?>> startFutures = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> endFutures   = new ConcurrentHashMap<>();

    private final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

     // ✅ Lors du démarrage de l’application :
    // on recharge les planifications encore actives (EN_ATTENTE, EN_COURS)
    // et on reprogramme automatiquement leurs exécutions.
    @PostConstruct
    public void init() {
        List<Planification> tasks = repository.findByStatutIn(Arrays.asList("EN_ATTENTE", "EN_COURS"));
        tasks.forEach(this::scheduleStartAndEnd);
    }

    // 🔹 Annuler toutes les tâches planifiées en mémoire lors du shutdown de l’application (En production, tu peux redéployer ton service ou arrêter le serveur)
    @PreDestroy
    public void shutdownAll() {
        startFutures.values().forEach(f -> f.cancel(false));
        endFutures.values().forEach(f -> f.cancel(false));
    }

     // ✅ CRÉATION d’une planification :
    //  - sauvegarde en BDD avec statut "EN_ATTENTE"
    //  - planifie l’exécution à dateDebut/heureDebut et dateFin/heureFin
    public PlanificationDto createPlanification(Planification plan) {
        plan.setDateCreation(LocalDateTime.now().toString());
        Employe employe = employeServices.getBycodeSecret(plan.getCodeSecret());
        // employe.deplacement = true au moment de la création de la planification, AVANT le début réel du déplacement.
        employe.setDeplacement(true);

        employeServices.save(employe);

        plan.setStatut(Planification.Statut.EN_ATTENTE);
        Planification saved = repository.save(plan);
        scheduleStartAndEnd(saved);
        return PlanificationDto.fromEntity(saved);
    }

    // ✅ ANNULATION manuelle :
    // met le statut en "ANNULEE" et stoppe toutes les tâches futures prévues
    public boolean cancelPlanification(String id, String motif) {
        Optional<Planification> optPlanif = repository.findById(id);

        if (optPlanif.isPresent()) {
            Planification planif = optPlanif.get();
            planif.setMotifAnnulation(motif);
            planif.setStatut(Planification.Statut.ANNULEE);
            repository.save(planif);

            cancelScheduled(id);
            return true;
        }
        return false;
    }

    // Annuler toutes les exécutions futures pour une planification donnée via son id
    void cancelScheduled(String id) {
        safeCancel(startFutures, id, false);
        safeCancel(endFutures, id, false);
    }

    private void safeCancel(Map<String, ScheduledFuture<?>> map, String id, boolean interrupt) {
        ScheduledFuture<?> f = map.remove(id);
        if (f != null && !f.isDone()) {
            f.cancel(interrupt);
        }
    }

    // ✅ PLANIFICATION D’UNE TÂCHE :
    // crée deux événements programmés :
    //  - un au "dateDebut + heureDebut"
    //  - un au "dateFin + heureFin"
    void scheduleStartAndEnd(Planification plan) {
        LocalDateTime dtStart = parseToDateTime(plan.getDateDebut(), plan.getHeureDebut());
        LocalDateTime dtEnd   = parseToDateTime(plan.getDateFin(), plan.getHeureFin());

        if (dtEnd.isBefore(dtStart)) {
            plan.setStatut(Planification.Statut.EXECUTEE);
            repository.save(plan);
            return;
        }

        Instant now = Instant.now(clock);
        Instant startInstant = dtStart.atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant   = dtEnd.atZone(ZoneId.systemDefault()).toInstant();

        if (now.isBefore(startInstant)) {
            plan.setStatut(Planification.Statut.EN_ATTENTE);
            repository.save(plan);

            scheduleAndTrack(() -> startTask(plan), startInstant, startFutures, plan.getId());
            scheduleAndTrack(() -> endTask(plan), endInstant, endFutures, plan.getId());

        } else if (!now.isBefore(startInstant) && now.isBefore(endInstant)) {
            plan.setStatut(Planification.Statut.EN_COURS);
            repository.save(plan);

            scheduleAndTrack(() -> endTask(plan), endInstant, endFutures, plan.getId());

        } else {
            plan.setStatut(Planification.Statut.EXECUTEE);
            repository.save(plan);
        }
    }

   /**
 * Programme une tâche unique à exécuter à une date/heure précise
 * et garde sa référence pour pouvoir la suivre ou l'annuler.
 *
 * @param task  -> La tâche à exécuter
 * @param at    -> L'instant précis (Instant) où la tâche doit démarrer
 * @param store -> Le registre (Map) qui garde les tâches planifiées
 * @param id    -> L'identifiant unique de la tâche (clé dans la Map)
 * @return      -> Le ScheduledFuture représentant la tâche planifiée
 */
 private ScheduledFuture<?> scheduleAndTrack(
        Runnable task, // le code métier que tu veux planifier et exécuter plus tard (start/end de ta planification, ou autre logique).
        Instant at,
        Map<String, ScheduledFuture<?>> store,
        String id) {

    // 🔹 On encapsule la tâche dans un "wrapper"
    // pour gérer automatiquement les erreurs et nettoyer le registre
    Runnable wrapper = () -> {
        try {
            task.run(); // ✅ Exécution réelle de la tâche
        } catch (Exception ex) {
            // ⚠️ Si une erreur survient, on la log
            log.error("Erreur lors de l'exécution de scheduleAndTrack", ex);
        }
    };

    // 🔹 On programme l'exécution avec TaskScheduler
    ScheduledFuture<?> future = taskScheduler.schedule(wrapper, at);

    // 🔹 On stocke la référence dans la Map pour suivi (pause, annulation, etc.)
    store.put(id, future);

    return future; // ✅ On renvoie la tâche planifiée pour un suivi externe si besoin
}

    /**
     * Calcule la différence entre deux heures au format HH:mm
     * @param start Heure de début (ex: "08:30")
     * @param end Heure de fin (ex: "12:15")
     * @return différence formatée HH:mm (ex: "03:45")
     */
    public  String difference(String start, String end) {
        LocalTime t1 = LocalTime.parse(start); // "HH:mm"
        LocalTime t2 = LocalTime.parse(end);

        Duration diff = Duration.between(t1, t2);

        // si négatif (ex: 23:00 → 01:00), on ajoute 24h
        if (diff.isNegative()) {
            diff = diff.plusDays(1);
        }

        long hours = diff.toHours();
        long minutes = diff.toMinutesPart();

        return String.format("%02d:%02d", hours, minutes);

    }



    /**
     * Calcule la différence en jours entre deux dates ISO
     * @param dateDebut première date (format yyyy-MM-dd'T'HH:mm:ss.SSSX)
     * @param dateFin deuxième date (format yyyy-MM-dd'T'HH:mm:ss.SSSX)
     * @return nombre de jours (peut être négatif si end < start)
     */
    public long differenceInDays(Date dateDebut, Date dateFin) {
        LocalDate debut = dateDebut.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate fin = dateFin.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return ChronoUnit.DAYS.between(debut, fin);
    }

    // Savoir si heure1 vient heure2 ou après
    public int comparerHeures(String heure1, String heure2) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime t1 = LocalTime.parse(heure1, formatter);
        LocalTime t2 = LocalTime.parse(heure2, formatter);

        return t1.compareTo(t2);
    }

    // Multiplier les heures supplémentaires au nombre de jour total (qui est équivaut à la durée du planning ex: 5 jours) pour
    // obtenir les heures supplémentaires pendant total pendant la durée du planning.
    public String multiplierHeure(String heure, long facteur) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        LocalTime time = LocalTime.parse(heure, formatter);

        // Convertir en minutes
        int minutes = time.getHour() * 60 + time.getMinute();

        // Multiplier
        long totalMinutes = minutes * facteur;

        // Reconvertir en heures et minutes
        long heures = totalMinutes / 60;
        long mins = totalMinutes % 60;

        // Si tu veux un format limité à 24h, applique % 24
        // heures = heures % 24;

        return String.format("%02d:%02d", heures, mins);
    }




    private void startTask(Planification planification) {

        if (planification == null || planification.getId() == null) {
            log.warn("⚠️ Planification invalide");
            return;
        }

        Planification plan = repository.findById(planification.getId())
                .orElseThrow(() -> new IllegalStateException("Planification introuvable : " + planification.getId()));

        if (plan.getStatut() == Planification.Statut.ANNULEE
                || plan.getStatut() == Planification.Statut.EN_COURS
                || plan.getStatut() == Planification.Statut.EXECUTEE) {
            return;
        }

        if (plan.getSiteDestination() == null || plan.getSiteDestination().length == 0) {
            throw new IllegalStateException("Site destination manquant pour " + plan.getId());
        }

        try {

            Employe employe = employeServices.getBycodeSecret(plan.getCodeSecret());
            if (employe == null) {
                log.warn("⚠️ Employé introuvable pour {}", plan.getCodeSecret());
                return;
            }

            Agence agenceDepart = agencesServices.getByNom(plan.getNomSite());
            Agence agenceArrivee = agencesServices.getByNom(plan.getSiteDestination()[0]);

            plan.setStatut(Planification.Statut.EN_COURS);
            repository.save(plan);

            if (!agenceDepart.getNom().equals(agenceArrivee.getNom())) {

                agenceDepart.setDeplacementEmploye(true);
                agenceArrivee.setReceptionEmploye(true);

                agencesRepository.save(agenceDepart);
                agencesRepository.save(agenceArrivee);

            } else {

                agenceDepart.setDeplacementInterne(true);
                agencesRepository.save(agenceDepart);

            }

            // Gestion remplacement
            String[] parts = plan.getPersonneRemplacee().split(" ");
            Employe employeRemplace = employeServices.employeeRemplacee(parts[0], parts[1]);

            if (employeRemplace != null) {
                employeRemplace.setRemplacement(true);
                employeRepository.save(employeRemplace);
            }

            // Déplacement employé
            employe.setSiteAvantDeplacement(plan.getNomSite());
            employe.setSite(plan.getSiteDestination());
            employe.setDeplacement(true);
            employe.setHorairesDeRemplacement(plan.getHeureDebut() + "-" + plan.getHeureFin());
            employe.setPersonneRemplacee(plan.getPersonneRemplacee());

            employeServices.save(employe);

            log.info("✅ Début planification {} pour employé {}", plan.getId(), employe.getCodeSecret());

        } catch (Exception ex) {
            log.error("❌ Erreur startTask {}", plan.getId(), ex);
        }

        startFutures.remove(plan.getId());
    }


    private void endTask(Planification planification) {

        if (planification == null || planification.getId() == null) {
            log.warn("⚠️ Planification invalide");
            return;
        }

        Planification plan = repository.findById(planification.getId())
                .orElseThrow(() -> new IllegalStateException("Planification introuvable : " + planification.getId()));

        if (plan.getStatut() == Planification.Statut.ANNULEE
                || plan.getStatut() == Planification.Statut.EXECUTEE) {
            return;
        }

        try {

            Employe employe = employeServices.getBycodeSecret(plan.getCodeSecret());
            if (employe == null) {
                log.warn("⚠️ Employé introuvable pour {}", plan.getCodeSecret());
                return;
            }

            Agence agenceDepart = agencesServices.getByNom(plan.getNomSite());
            Agence agenceArrivee = agencesServices.getByNom(plan.getSiteDestination()[0]);

            plan.setStatut(Planification.Statut.EXECUTEE);
            repository.save(plan);

            if (!agenceDepart.getNom().equals(agenceArrivee.getNom())) {

                agenceDepart.setDeplacementEmploye(false);
                agenceArrivee.setReceptionEmploye(false);

                agencesRepository.save(agenceDepart);
                agencesRepository.save(agenceArrivee);

            } else {

                agenceDepart.setDeplacementInterne(false);
                agencesRepository.save(agenceDepart);

            }

            // Gestion remplacement
            String[] parts = plan.getPersonneRemplacee().split(" ");
            Employe employeRemplace = employeServices.employeeRemplacee(parts[0], parts[1]);

            if (employeRemplace != null) {
                employeRemplace.setRemplacement(false);
                employeRepository.save(employeRemplace);
            }

            // Retour employé
            if (employe.getSiteAvantDeplacement() != null) {
                employe.setSite(new String[]{employe.getSiteAvantDeplacement()});
            }

            employe.setDeplacement(false);
            employe.setHorairesDeRemplacement(null);
            employe.setPersonneRemplacee(null);

            employeServices.save(employe);

            log.info("✅ Fin planification {} pour employé {}", plan.getId(), employe.getCodeSecret());

        } catch (Exception ex) {
            log.error("❌ Erreur endTask {}", plan.getId(), ex);
        }

        endFutures.remove(plan.getId());
    }

      // ✅ Helper : conversion Date + "HH:mm" → LocalDateTime
    private LocalDateTime parseToDateTime(Date date, String timeStr) {
        // Convertir java.util.Date -> LocalDate
        LocalDate localDate = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        // Parser l'heure (ex: "08:30")
        LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));

        // Fusionner les deux
        return LocalDateTime.of(localDate, time);
    }

    public List<PlanificationDto> getAll() {
        return repository.findAll()
                .stream()
                .map(PlanificationDto::fromEntity)
                .toList();
    }

    public PlanificationDto getById(String id) {
        Planification plan = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Planification non trouvée"));
        return PlanificationDto.fromEntity(plan);
    }

    // ✅ MISE À JOUR :
    // si les dates ou heures changent → on annule les anciennes exécutions
    // et on reprogramme avec les nouvelles valeurs.
    public Optional<PlanificationDto> updatePlanification(String id, Planification updated) {
        // 1️⃣ Récupérer la planification existante depuis la base de données
        return repository.findById(id).map(existing -> {

            // 2️⃣ Annuler les futures planifiées (start/end) de l'ancienne version
            //    Cela empêche l'exécution automatique basée sur les anciennes dates/heures
            cancelScheduled(existing.getId());

            // 3️⃣ Mettre à jour les champs de la planification
            //    Ici tu peux mettre à jour toutes les données personnelles ou planification
            existing.setPrenomNom(updated.getPrenomNom());        // nom de l'employé
            existing.setNomSite(updated.getNomSite());            // site de travail
            existing.setSiteDestination(updated.getSiteDestination());
            existing.setDateDebut(updated.getDateDebut());        // nouvelle date de début
            existing.setHeureDebut(updated.getHeureDebut());      // nouvelle heure de début
            existing.setDateFin(updated.getDateFin());            // nouvelle date de fin
            existing.setHeureFin(updated.getHeureFin());          // nouvelle heure de fin
            existing.setCommentaires(updated.getCommentaires());  // mise à jour des commentaires

            // 4️⃣ Réinitialiser le statut à EN_ATTENTE si les dates/heures ont changé
            //    Cela permet de replanifier correctement la tâche dans le scheduler
            existing.setStatut(Planification.Statut.EN_ATTENTE);

            // 5️⃣ Sauvegarder les modifications dans la base de données
            Planification saved = repository.save(existing);

            // 6️⃣ Re-planifier les exécutions automatiques à partir des nouvelles dates/heures
            scheduleStartAndEnd(saved);

            // 7️⃣ Retourner la planification mise à jour
            return PlanificationDto.fromEntity(saved);
        });
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public List<AnnulationRequestMessage> getPendingAnnulations() {
        List<Planification> pendingPlans = repository.findByStatut(Planification.Statut.EN_ATTENTE_VALIDATION);


        return pendingPlans.stream().map(p -> {
            AnnulationRequestMessage dto = new AnnulationRequestMessage();
            dto.setPlanificationId(p.getId());
            dto.setMotif(p.getMotifAnnulation());
            dto.setRequestedBy(p.getRequestedBy());
            dto.setDateRequest(p.getDateDemandeAnnulation());
            return dto;
        }).collect(Collectors.toList());
    }

    // Admin envoie une demande d'annulation (EN_ATTENTE_VALIDATION) au super admin.
    public CancelRequestDto demanderAnnulation(String id, String motif, String requestedBy) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                .withZone(ZoneId.systemDefault());

        Planification plan = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Planification non trouvée"));
        plan.setMotifAnnulation(motif);
        plan.setStatut(Planification.Statut.EN_ATTENTE_VALIDATION);
        plan.setRequestedBy(requestedBy);
        plan.setDateDemandeAnnulation(formatter.format(Instant.now()));
        repository.save(plan);

        CancelRequestDto msg = new CancelRequestDto(); // Cet objet représente la requête d’annulation que tu veux envoyer aux super-admins.
        msg.setPlanificationId(plan.getId());
        msg.setMotif(motif);
        msg.setRequestedBy(requestedBy);


        // broadcast aux super-admins
        /**
         * convertAndSend(destination, payload) →
         * destination = le “canal” ou “topic” où tu publies le message (/topic/annulationRequests).
         * payload = l’objet à envoyer (msg), qui sera converti en JSON automatiquement.
         * Ce que ça veut dire en pratique:
         * Dès qu’un utilisateur envoie une demande d’annulation, tu construis ce message.
         * Tu le diffuses sur la chaîne WebSocket /topic/annulationRequests.
         * Tous les super-admins connectés et abonnés à ce topic recevront le JSON, en temps réel.
         */
        messagingTemplate.convertAndSend("/topic/annulationRequests", msg);

        return msg;
    }

    // Super admin valide/refuse
    public PlanificationDto validerAnnulation(String id, boolean accepte, String validatedBy) {
        Planification plan = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Planification non trouvée"));

        if (accepte) {
            plan.setStatut(Planification.Statut.ANNULEE);
            plan.setStatut(Planification.Statut.ANNULATION_ACCEPTEE);
        } else {
            plan.setStatut(Planification.Statut.ANNULATION_REFUSEE);
            // optionnel : plan.setMotifAnnulation(null);
        }
        plan.setValidatedBy(validatedBy);
        repository.save(plan);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                .withZone(ZoneId.systemDefault());

        AnnulationDecisionMessage decision = new AnnulationDecisionMessage();
        decision.setPlanificationId(plan.getId());
        decision.setAccepted(accepte);
        decision.setValidatedBy(validatedBy);
        decision.setMotif(plan.getMotifAnnulation());
        decision.setDateDecision(formatter.format(Instant.now()));

        // notifier l'admin demandeur en user queue
        if (plan.getRequestedBy() != null) {
            /**
             * 2️⃣ convertAndSendToUser(user, destination, payload)
             * Contrairement à convertAndSend (qui envoie à tout un topic),
             * 👉 ici tu envoies le message à un seul utilisateur en particulier.
             * user → c’est l’identifiant de l’utilisateur (souvent son username ou son id de session).
             * destination → le chemin où l’utilisateur doit écouter (souvent sous /user/queue/...).
             * payload → le contenu du message (ici decision, ça peut être "acceptée" ou "refusée" par exemple).
             * 3️⃣ plan.getRequestedBy()
             * ➡️ Ici tu récupères l’utilisateur qui a demandé l’annulation (celui qui a fait la requête).
             * C’est lui qui doit recevoir la réponse.
             * 4️⃣ "/queue/annulationResponses"
             * ➡️ C’est la file privée de messages où l’utilisateur concerné est abonné.
             * Dans STOMP, chaque utilisateur a son propre espace /user/{username}/queue/....
             * Donc si requestedBy = "employe1",
             * Spring enverra réellement sur :
             * /user/employe1/queue/annulationResponses
             * 5️⃣ decision
             * ➡️ C’est la réponse du super-admin : par exemple "ACCEPTEE" ou "REFUSEE".
             * 🔄 Exemple complet du cycle
             * Employé → envoie une demande d’annulation.
             * Super-admins → reçoivent la notification en temps réel via /topic/annulationRequests.
             * L’un d’eux prend une décision (acceptée ou refusée).
             * Le backend envoie la réponse directement à l’utilisateur qui a demandé, via :
             * convertAndSendToUser("employe1", "/queue/annulationResponses", "ACCEPTEE");
             * Employé employe1 → reçoit en direct la réponse sur sa WebSocket.
             * 👉 Donc :
             * convertAndSend() = broadcast (à tous ceux qui écoutent un topic).
             * convertAndSendToUser() = message privé (un seul utilisateur abonné).
             */
            messagingTemplate.convertAndSendToUser(plan.getRequestedBy(), "/queue/annulationResponses", decision);
        }

        // broadcast pour synchro UI super-admins. Ça veut dire : on envoie le message à tous les super-admins connectés pour qu’ils synchronisent leur interface utilisateur (UI).
        //Par exemple, si un admin accepte une demande, tous les autres voient le changement instantanément.
        /**
         * messagingTemplate.convertAndSend(...)
         * messagingTemplate → l’outil Spring qui publie des messages WebSocket/STOMP.
         * convertAndSend(destination, payload) → envoie un message à tous les abonnés d’un canal (topic).
         * "/topic/annulationDecisions"
         * C’est le canal partagé où les super-admins sont abonnés.
         * Tous ceux qui écoutent /topic/annulationDecisions recevront les décisions en temps réel.
         * decision: le contenu envoyé (payload).
         */
        messagingTemplate.convertAndSend("/topic/annulationDecisions", decision);

        return PlanificationMapper.toDto(plan);


        /**
         * 🌀 Exemple de flux complet
         * Employé fait une demande d’annulation.
         * Tous les super-admins reçoivent la demande en temps réel via /topic/annulationRequests.
         * L’un d’eux prend une décision.
         * Le backend envoie :
         * convertAndSendToUser(..., "/queue/annulationResponses", decision) → uniquement à l’employé concerné.
         * convertAndSend("/topic/annulationDecisions", decision) → à tous les super-admins, pour que leur tableau de bord se mette à jour (broadcast).
         * 👉 En résumé :
         * convertAndSendToUser = réponse privée (employé).
         * convertAndSend = broadcast (tous les super-admins).
         */
    }
}














/*

Explications simples

scheduler → s’occupe de lancer automatiquement les traitements au bon moment
scheduleTask(task) → planifie startTask à dateDebut et endTask à dateFin
startTask(task) → change le statut en EN_COURS et fait le traitement métier du début
endTask(task) → change le statut en EXECUTEE et fait le traitement métier de fin
Annulation → si le statut est ANNULEE, rien ne se passe
C’est donc très simple à retenir :
“Pour chaque tâche, on programme exactement quand commencer et quand finir. Le scheduler s’occupe du reste.”

* */

/*package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.repositories.PlanificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanificationService {

    private final MongoTemplate mongoTemplate;
    private final PlanificationRepository planificationRepository;

    public Planification save(Planification planification) {
        return mongoTemplate.save(planification);
    }

    public List<Planification> getAll() {
        return mongoTemplate.findAll(Planification.class);
    }

    public Planification getBycodeSecret(String codeSecret) {
        Query query = new Query();
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret));
        return mongoTemplate.findOne(query,Planification.class);
    }


    public List<Planification> PlanificationsAVenir(String codeSecret) {
        Date now = new Date();

        Query query = new Query();
        query.addCriteria(
                Criteria.where("codeSecret").is(codeSecret)
                        .and("dateDebut").gt(now)
        );

        return mongoTemplate.find(query, Planification.class);
    }


    public List<Planification> PlanificationsEnCours(String codeSecret) {
        Date now = new Date();

        Query query = new Query();
        query.addCriteria(
                Criteria.where("codeSecret").is(codeSecret)
                        .and("dateDebut").lte(now)
                        .and("dateFin").gte(now)
        );

        return mongoTemplate.find(query, Planification.class);
    }

    public List<Planification> PlanificationsTerminees(String codeSecret) {
        Date now = new Date();

        Query query = new Query();
        query.addCriteria(
                Criteria.where("codeSecret").is(codeSecret)
                        .and("dateFin").lt(now)
        );

        return mongoTemplate.find(query, Planification.class);
    }



}*/

/*
package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.repositories.PlanificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;


import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
public class PlanificationService {

    private final PlanificationRepository repository;
    private final TaskScheduler taskScheduler;
    // ✅ Spring fournit un TaskScheduler (basé sur ScheduledExecutorService)
    // qui permet de planifier des tâches à exécuter à une date précise.

    // ✅ Ces deux Maps conservent les "ScheduledFuture" (les tâches planifiées en mémoire)
    // pour pouvoir les annuler si la planification change ou est supprimée.
    private final Map<String, ScheduledFuture<?>> startFutures = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> endFutures   = new ConcurrentHashMap<>();

    // ✅ Format utilisé pour parser les dates et heures ISO (entrées en string par l’utilisateur)
    private final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ✅ Lors du démarrage de l’application :
    // on recharge les planifications encore actives (EN_ATTENTE, EN_COURS)
    // et on reprogramme automatiquement leurs exécutions.
    @PostConstruct
    public void init() {
        List<Planification> tasks = repository.findByStatutIn(Arrays.asList("EN_ATTENTE", "EN_COURS"));
        tasks.forEach(this::scheduleStartAndEnd);
    }

    // ✅ CRÉATION d’une planification :
    //  - sauvegarde en BDD avec statut "EN_ATTENTE"
    //  - planifie l’exécution à dateDebut/heureDebut et dateFin/heureFin
    public Planification createPlanification(Planification plan) {
        plan.setDateCreation(LocalDateTime.now().toString());
        plan.setStatut(Planification.Statut.EN_ATTENTE);
        Planification saved = repository.save(plan);
        scheduleStartAndEnd(saved); // programme automatiquement la tâche
        return saved;
    }

    // ✅ MISE À JOUR :
    // si les dates ou heures changent → on annule les anciennes exécutions
    // et on reprogramme avec les nouvelles valeurs.
    public Optional<Planification> updatePlanification(String id, Planification updated) {
        // 1️⃣ Récupérer la planification existante depuis la base de données
        return repository.findById(id).map(existing -> {

            // 2️⃣ Annuler les futures planifiées (start/end) de l'ancienne version
            //    Cela empêche l'exécution automatique basée sur les anciennes dates/heures
            cancelScheduled(existing.getId());

            // 3️⃣ Mettre à jour les champs de la planification
            //    Ici tu peux mettre à jour toutes les données personnelles ou planification
            existing.setPrenomNom(updated.getPrenomNom());        // nom de l'employé
            existing.setNomSite(updated.getNomSite());            // site de travail
            existing.setDateDebut(updated.getDateDebut());        // nouvelle date de début
            existing.setHeureDebut(updated.getHeureDebut());      // nouvelle heure de début
            existing.setDateFin(updated.getDateFin());            // nouvelle date de fin
            existing.setHeureFin(updated.getHeureFin());          // nouvelle heure de fin
            existing.setCommentaires(updated.getCommentaires());  // mise à jour des commentaires

            // 4️⃣ Réinitialiser le statut à EN_ATTENTE si les dates/heures ont changé
            //    Cela permet de replanifier correctement la tâche dans le scheduler
            existing.setStatut(Planification.Statut.EN_ATTENTE);

            // 5️⃣ Sauvegarder les modifications dans la base de données
            Planification saved = repository.save(existing);

            // 6️⃣ Re-planifier les exécutions automatiques à partir des nouvelles dates/heures
            scheduleStartAndEnd(saved);

            // 7️⃣ Retourner la planification mise à jour
            return saved;
        });
    }


    // ✅ ANNULATION manuelle :
    // met le statut en "ANNULEE" et stoppe toutes les tâches futures prévues
    public boolean cancelPlanification(String id) {
        Optional<Planification> opt = repository.findById(id);
        if (opt.isEmpty()) return false;
        Planification p = opt.get();
        p.setStatut(Planification.Statut.ANNULEE);
        repository.save(p);

        // on supprime les tâches futures en mémoire
        cancelScheduled(id);
        return true;
    }

    // ✅ Annuler toutes les exécutions futures pour une planification donnée via son id
    private void cancelScheduled(String id) {
        ScheduledFuture<?> f = startFutures.remove(id);
        if (f != null) f.cancel(false);
        f = endFutures.remove(id);
        if (f != null) f.cancel(false);
    }

    // ✅ PLANIFICATION D’UNE TÂCHE :
    // crée deux événements programmés :
    //  - un au "dateDebut + heureDebut"
    //  - un au "dateFin + heureFin"
    private void scheduleStartAndEnd(Planification plan) {
        // parsing date+heure en LocalDateTime
        LocalDateTime dtStart = parseToDateTime(plan.getDateDebut(), plan.getHeureDebut());
        LocalDateTime dtEnd   = parseToDateTime(plan.getDateFin(), plan.getHeureFin());

        // si la fin est avant le début → tâche considérée comme déjà exécutée
        if (dtEnd.isBefore(dtStart)) {
            plan.setStatut(Planification.Statut.EXECUTEE);
            repository.save(plan);
            return;
        }

        Instant now = Instant.now();
        Instant startInstant = dtStart.atZone(ZoneId.systemDefault()).toInstant();
        Instant endInstant   = dtEnd.atZone(ZoneId.systemDefault()).toInstant();

        if (now.isBefore(startInstant)) {
            // Avant le début : statut EN_ATTENTE, on planifie le début et la fin
            plan.setStatut(Planification.Statut.EN_ATTENTE);
            repository.save(plan);

            ScheduledFuture<?> startFuture = taskScheduler.schedule(() -> startTask(plan.getId()), startInstant);
            startFutures.put(plan.getId(), startFuture);

            ScheduledFuture<?> endFuture = taskScheduler.schedule(() -> endTask(plan.getId()), endInstant);
            endFutures.put(plan.getId(), endFuture);

        } else if (!now.isBefore(startInstant) && now.isBefore(endInstant)) {
            // Entre le début et la fin : on démarre directement en EN_COURS, et on planifie seulement la fin
            plan.setStatut(Planification.Statut.EN_COURS);
            repository.save(plan);

            ScheduledFuture<?> endFuture = taskScheduler.schedule(() -> endTask(plan.getId()), endInstant);
            endFutures.put(plan.getId(), endFuture);

        } else {
            // Après la fin → déjà exécutée
            plan.setStatut(Planification.Statut.EXECUTEE);
            repository.save(plan);
        }
    }

    // ✅ TÂCHE DE DÉMARRAGE (exécutée exactement à dateDebut + heureDebut)
    private void startTask(String planId) {
        repository.findById(planId).ifPresent(plan -> {
            if ("ANNULEE".equals(plan.getStatut()) || "EN_COURS".equals(plan.getStatut()) || "EXECUTEE".equals(plan.getStatut())) {
                // déjà annulée, en cours ou terminée → rien à faire
            } else {
                // passage en statut "EN_COURS"
                plan.setStatut(Planification.Statut.EN_COURS);
                repository.save(plan);

                // --- TRAITEMENT MÉTIER DE DÉBUT ---
                try {
                    // 👉 ici tu mets ton code métier (ex : notifier, activer ressource, loguer un événement, etc.)
                    System.out.println("▶️ Début du traitement métier pour " + plan.getId() + " à " + LocalDateTime.now());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        // on supprime la référence car la tâche est déjà exécutée
        startFutures.remove(planId);
    }

    // ✅ TÂCHE DE FIN (exécutée exactement à dateFin + heureFin)
    private void endTask(String planId) {
        repository.findById(planId).ifPresent(plan -> {
            if ("ANNULEE".equals(plan.getStatut()) || "EXECUTEE".equals(plan.getStatut())) {
                // déjà annulée ou déjà terminée → rien
            } else {
                // passage en statut "EXECUTEE"
                plan.setStatut(Planification.Statut.EXECUTEE);
                repository.save(plan);

                // --- TRAITEMENT MÉTIER DE FIN ---
                try {
                    // 👉 ici tu mets ton code métier (ex : désactiver ressource, envoyer rapport, clôturer session, etc.)
                    System.out.println("✅ Fin du traitement métier pour " + plan.getId() + " à " + LocalDateTime.now());
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        // on supprime la référence car la tâche est déjà exécutée
        endFutures.remove(planId);
    }

    // ✅ Helper : conversion Date + "HH:mm" → LocalDateTime
    private LocalDateTime parseToDateTime(Date date, String timeStr) {
        // Convertir java.util.Date -> LocalDate
        LocalDate localDate = date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        // Parser l'heure (ex: "08:30")
        LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));

        // Fusionner les deux
        return LocalDateTime.of(localDate, time);
    }

    // ✅ Récupérer toutes les planifications
    public List<Planification> getAll() {
        return repository.findAll();
    }

    // ✅ Récupérer une planification par ID
    public Optional<Planification> getById(String id) {
        return repository.findById(id);
    }
}
 */
