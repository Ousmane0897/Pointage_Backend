package com.example.Pointage_Cleanic.entities;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "employes-complet")
public class EmployeComplet {

    @Id
    private String id;

    private String agentId;
    private String matricule;
    private String prenom;
    private String nom;
    private String sexe;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant dateNaissance;

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
    private String agence;
    private String codeSite;
    private String villeSite;
    private String chefEquipe;
    private String managerOps;
    private String poste;
    private String typeContrat;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant dateEmbauche;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant dateFinContrat;

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
    private PermisConduire permisConduire;
    private String categoriePermis;
    private StatutEmploye statut;
    private String motifSortie;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant dateSortie;

    private byte[] photo;
    private String observations;

    public enum PermisConduire {
        OUI, NON
    }

    public enum StatutEmploye {
        ACTIF, Pause, Sortie
    }
}