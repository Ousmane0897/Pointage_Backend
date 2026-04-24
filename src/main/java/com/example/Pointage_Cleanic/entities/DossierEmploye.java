package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.GenreEmploye;
import com.example.Pointage_Cleanic.Enum.SituationMatrimoniale;
import com.example.Pointage_Cleanic.Enum.StatutDossierEmploye;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "dossiers_employes")
public class DossierEmploye {

    @Id
    private String id;

    @Indexed(unique = true)
    private String matricule;

    // Identité
    private String nom;
    private String prenom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateNaissance;

    private GenreEmploye genre;
    private String nationalite;
    private String numeroIdentification;
    private SituationMatrimoniale situationMatrimoniale;
    private Integer nombreEnfants;

    // Poste
    private String poste;
    private String departement;
    private String siteAffecte;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEntree;

    private StatutDossierEmploye statut;
    private String superieurHierarchiqueId;
    private String superieurHierarchiqueNom;
    private Integer dureeEssaiMois;

    // Contacts
    private String telephone;
    private String email;
    private String adresse;

    // Urgence
    private ContactUrgence contactUrgence;

    // Photo (stockée en byte[], exposée via endpoint dédié)
    private byte[] photo;
}