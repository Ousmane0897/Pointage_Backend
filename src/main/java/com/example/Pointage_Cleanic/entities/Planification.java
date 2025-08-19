package com.example.Pointage_Cleanic.entities;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.Date;

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
    private String siteDestination;


    @Field("dateDebut")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private Date dateDebut;

    @Field("dateFin")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private Date dateFin;

    private String heureDebut;       // Heure de début de chaque journée
    private String heureFin;         // Heure de fin prévue


    private String commentaires;         // Commentaires du superviseur ou de l’agent

    private String statut;              // ex : "Prévu", "En cours", "Terminé", "Annulé"

    //private String creePar;             // Nom ou ID du planificateur
    private String dateCreation;

}
