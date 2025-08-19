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
    private String prenom;
    private String nom;
    private String numero;
    private String intervention; //agent bureau, vitre ou désinfectation
    private String statut; // employé simple ou chef d'équipe
    private String employeCreePar;
    private String[] site;
    private String joursDeTravail;
    private String joursDeTravail2;
    private boolean deplacement; // Si un agent à été déplacé d'une agence à une autre
    private String heureDebut;
    private String heureFin;
    private String heureDebut2;
    private String heureFin2;
    private String dateEtHeureCreation;


}
