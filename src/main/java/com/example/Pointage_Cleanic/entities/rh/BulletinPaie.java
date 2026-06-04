package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutBulletin;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "bulletins_paie")
@CompoundIndexes({
        @CompoundIndex(name = "employe_periode_idx",
                def = "{'employeId': 1, 'periode.annee': 1, 'periode.mois': 1}")
})
public class BulletinPaie {

    @Id
    private String id;

    @Indexed
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

    @Builder.Default
    private List<LigneBulletin> lignes = new ArrayList<>();

    // Snapshots des rubriques de retenues personnelles appliquées sur
    // ce bulletin (traçabilité : la grille peut être modifiée
    // ultérieurement sans impacter les bulletins déjà calculés)
    @Builder.Default
    private List<PretCategorie> pretsAppliques = new ArrayList<>();

    @Builder.Default
    private List<AvanceCategorie> avancesAppliquees = new ArrayList<>();

    @Builder.Default
    private List<RetenueCategorie> retenuesAppliquees = new ArrayList<>();

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