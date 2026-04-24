package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.GenreEmploye;
import com.example.Pointage_Cleanic.Enum.SituationMatrimoniale;
import com.example.Pointage_Cleanic.Enum.StatutDossierEmploye;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DossierEmployeDto {

    private String id;
    private String matricule;

    // Identité
    private String nom;
    private String prenom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateNaissance;

    private GenreEmploye genre;
    private String nationalite;
    private String photoUrl;
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
    private ContactUrgenceDto contactUrgence;
}