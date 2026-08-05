package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
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
public class DemandeCongeDto {

    private String id;
    private String employeId;
    private String matricule;
    private String nom;
    private String prenom;
    private String departement;
    private TypeConge type;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private Integer nombreJours;
    private String motif;
    private StatutDemande statut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDemande;

    // ─── Circuit de validation à 3 niveaux ──────────────────────────────────

    /** Niveau attendu, dérivé du statut (null si la demande est terminale). */
    private NiveauValidationConge niveauCourant;

    private String superieurHierarchiqueId;
    private String superieurHierarchiqueNom;
    private Boolean niveauSuperieurIgnore;

    private DecisionNiveauDto decisionSuperieur;
    private DecisionNiveauDto decisionRh;
    private DecisionNiveauDto decisionDg;

    private NiveauValidationConge niveauRefus;
    private String motifRefus;

    private List<HistoriqueValidationCongeDto> historique;

    /**
     * Calculé pour l'appelant courant : autorité unique du front pour afficher les
     * boutons Valider / Refuser. Renseigné par le workflow service, jamais par le
     * mapping générique — il dépend de qui interroge.
     */
    private Boolean peutValiderParMoi;

    // ─── Décision finale, format historique ─────────────────────────────────

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDecision;

    private String decideurId;
    private String decideurNom;
    private String commentaireDecision;
}
