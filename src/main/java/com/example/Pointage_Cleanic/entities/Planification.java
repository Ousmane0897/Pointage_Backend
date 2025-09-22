package com.example.Pointage_Cleanic.entities;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "planification")
public class Planification {

    @Id
    private String id;
    private String prenomNom;
    private String codeSecret;
    // Informations de la mission

    private String nomSite;
    private String[] siteDestination;
    private String personneRemplacee;
    private boolean matin;
    private boolean apresMidi;


    @Field("dateDebut")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private Date dateDebut;

    @Field("dateFin")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private Date dateFin;

    private String heureDebut;       // Heure de début de chaque journée
    private String heureFin;         // Heure de fin prévue


    private String commentaires;         // Commentaires du superviseur ou de l’agent

    private String motifAnnulation;
    private Statut statut;            // ex : "Prévu", "En cours", "Terminé", "Annulé"

    private long joursRestants; // Jours restant avant le début de la tache (ex: j-7)

    //private String creePar;             // Nom ou ID du planificateur
    private String dateCreation;

    private AnnulationStatus annulationStatus = AnnulationStatus.NON_DEMANDEE;
    private String requestedBy; // L'admin qui envoie la demande d'annulation
    private String validatedBy; // Le super admin


    public enum Statut {
        EN_ATTENTE,
        EN_COURS,
        EXECUTEE,
        ANNULEE
    }


    public enum AnnulationStatus {
        NON_DEMANDEE, EN_ATTENTE_VALIDATION, VALIDEE, REFUSEE
    }


    /**
     * Calcule dynamiquement le nombre de jours restants avant le début de la planification
     * @return nombre de jours restants (j-10, j-09...), -1 si dateDebut non définie, 0 si déjà commencé
     */
    public long getJoursRestants() {
        if (dateDebut == null) return -1;

        LocalDate today = LocalDate.now();
        LocalDate debut = convertToLocalDate(dateDebut);

        return today.isAfter(debut) ? 0 : ChronoUnit.DAYS.between(today, debut);
    }

    /**
     * Convertit java.util.Date en java.time.LocalDate
     */
    public LocalDate convertToLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

}
