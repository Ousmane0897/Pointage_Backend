package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.StatutBulletin;
import com.example.Pointage_Cleanic.entities.AvanceCategorie;
import com.example.Pointage_Cleanic.entities.LigneBulletin;
import com.example.Pointage_Cleanic.entities.PeriodePaie;
import com.example.Pointage_Cleanic.entities.PretCategorie;
import com.example.Pointage_Cleanic.entities.RetenueCategorie;
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
public class BulletinPaieDto {

    private String id;

    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String poste;
    private String departement;
    private String categorieCode;
    private String numeroIpres;
    private String numeroCss;
    private String rib;
    private String banque;

    private PeriodePaie periode;

    private int joursTravailles;
    private int joursAbsence;
    private int joursConge;
    private double heuresSupTotal;
    private double heuresSupMajoreesEquivalent;

    private List<LigneBulletin> lignes;

    private List<PretCategorie> pretsAppliques;
    private List<AvanceCategorie> avancesAppliquees;
    private List<RetenueCategorie> retenuesAppliquees;
    private Long totalPrets;
    private Long totalAvances;
    private Long totalRetenues;

    private Long salaireBrut;
    private Long totalCotisationsSalariales;
    private Long totalCotisationsPatronales;
    private Long impotRevenu;
    private Long trimf;
    private Long netAPayer;
    private Long coutTotalEmployeur;

    private Long cumulBrutAnnuel;
    private Long cumulNetAnnuel;
    private Long cumulIrAnnuel;
    private Integer soldeConges;

    private StatutBulletin statut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCalcul;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateValidation;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate datePaiement;

    private String validateurId;
    private String validateurNom;
    private String commentaire;
}