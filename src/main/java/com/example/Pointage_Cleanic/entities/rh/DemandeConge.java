package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Demande de congé et son circuit de validation à 3 niveaux
 * ({@code EN_ATTENTE_SUPERIEUR → EN_ATTENTE_RH → EN_ATTENTE_DG → APPROUVE}).
 *
 * <p>Le validateur de niveau 1 ({@code superieurHierarchiqueId/Nom}) est <b>figé à la
 * création</b> : un changement d'organigramme ne doit jamais rerouter une demande en vol.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "conges")
public class DemandeConge {

    @Id
    private String id;

    @Indexed
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

    @Builder.Default
    private StatutDemande statut = StatutDemande.EN_ATTENTE;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDemande;

    // ─── Circuit de validation à 3 niveaux ──────────────────────────────────

    /** Validateur de niveau 1, figé à la création. */
    @Indexed
    private String superieurHierarchiqueId;

    private String superieurHierarchiqueNom;

    /** true si le demandeur n'avait pas de supérieur : le circuit démarre à la RH. */
    private Boolean niveauSuperieurIgnore;

    private DecisionNiveau decisionSuperieur;
    private DecisionNiveau decisionRh;
    private DecisionNiveau decisionDg;

    /** Renseignés uniquement si {@code statut == REFUSE}. */
    private NiveauValidationConge niveauRefus;
    private String motifRefus;

    @Builder.Default
    private List<HistoriqueValidationConge> historique = new ArrayList<>();

    // ─── Décision finale, format historique ─────────────────────────────────
    // Conservés pour les demandes antérieures au circuit ; alimentés en miroir
    // de la décision qui clôt la demande (approbation finale ou refus).

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDecision;

    private String decideurId;
    private String decideurNom;
    private String commentaireDecision;

    /** Ajoute une entrée d'historique en initialisant la liste si besoin. */
    public void tracer(HistoriqueValidationConge entree) {
        if (historique == null) {
            historique = new ArrayList<>();
        }
        historique.add(entree);
    }
}