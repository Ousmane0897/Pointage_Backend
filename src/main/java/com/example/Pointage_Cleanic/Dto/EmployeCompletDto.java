package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmployeCompletDto {

    private String id;

    private String agentId;
    private String matricule;
    private String prenom;
    private String nom;
    private String sexe;
    private String heureDebut;
    private String heureFin;
    private String heureDebut2;
    private String heureFin2;
    private String joursDeTravail;
    private String joursDeTravail2;


    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateNaissance;

    private String lieuNaissance;
    private String nationalite;
    private String etatCivil;
    private String adresse;
    private String ville;
    private String telephone1;
    private String telephone2;
    private String email;
    private String contactUrgence;
    private String lienDeParenteAvecContactUrgence;
    private String telephoneUrgent;
    private String[] agence;
    private String codeSite;
    private String villeSite;
    private String chefEquipe;
    private String managerOps;
    private String codeSite2;
    private String villeSite2;
    private String chefEquipe2;
    private String managerOps2;
    private String poste;
    private String typeContrat;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEmbauche;


    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFinContrat;

    private String tempsDeTravail;
    private String horaire;
    private String salaireDeBase;
    private String primeTransport;
    private String primeAssiduite;
    private String primeRisque;
    private String ribCompteBancaire;
    private String banque;
    private String cnssOuIpres;
    private String ipmNumero;

    private EmployeComplet.PermisConduire permisConduire;
    private String categoriePermis;

    private EmployeComplet.StatutEmploye statut;
    private String motifSortie;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateSortie;

    private byte[] photo;
    private String observations;

}
