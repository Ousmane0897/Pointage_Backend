package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.GenreEmploye;
import com.example.Pointage_Cleanic.Enum.rh.SituationMatrimoniale;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DossierEmployeDto {

    private String id;
    private String matricule;

    // Code agent à 4 chiffres = clé du pointage (^\d{4}$, unique)
    private String agentId;

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
    // Dérivé serveur (noms de affectations joints par " - "). Renvoyé en lecture ;
    // en écriture, recalculé depuis affectations si celles-ci sont présentes.
    private String siteAffecte;
    // Multi-sites avec tranche horaire optionnelle ("HH:mm", null autorisé).
    private List<AffectationSiteDto> affectations;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEntree;

    private StatutDossierEmploye statut;
    private String superieurHierarchiqueId;
    private String superieurHierarchiqueNom;
    private Integer dureeEssaiMois;

    // Rythme de travail hebdomadaire (LUN_VEN, LUN_SAM, LUN_DIM), validé côté
    // service contre l'enum JoursTravail. Nullable / optionnel (rétro-compat).
    private String joursTravail;

    // Contacts
    private String telephone;
    private String email;
    private String adresse;

    // Urgence
    private ContactUrgenceDto contactUrgence;

    // Données paie (optionnelles côté frontend, nécessaires au calcul
    // de bulletin lorsque présentes)
    private String categorieCode;
    private String numeroIpres;
    private String numeroCss;
    private String rib;
    private String banque;
}