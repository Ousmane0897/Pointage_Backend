package com.example.Pointage_Cleanic.entities;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "employes")
public class Employe {

    @Id
    private String id;
    private String codeSecret;
    private String agentId;
    private String prenom;
    private String nom;
    private String numero;
    private String intervention; //agent bureau, vitre ou désinfectation
    private String statut; // employé simple ou chef d'équipe
    private String employeCreePar;
    private String[] site;
    //private String siteDestination;
    private String siteAvantDeplacement;
    private String joursDeTravail;
    private String joursDeTravail2;
    private boolean matin;
    private boolean apresMidi;
    private boolean deplacement; // Si un agent a été déplacé d'une agence à une autre.
    private boolean remplacement; // Si un agent a été remplacé par une autre.
    private String heureDebut;
    private String heureFin;
    private String heureDebutAvantDeplacement;
    private String heureFinAvantDeplacement;
    private String heureDebut2;
    private String heureFin2;
    private String heureDebutAvantDeplacement2;
    private String heureFinAvantDeplacement2;
    private String horairesDeRemplacement;
    private String personneRemplacee;
    private String dateEtHeureCreation;
    private String heuresSupplementaires;


}
