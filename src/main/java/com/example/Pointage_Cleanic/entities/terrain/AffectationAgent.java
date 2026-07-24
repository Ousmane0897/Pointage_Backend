package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "terrain_affectations")
public class AffectationAgent {

    @Id
    private String id;

    private String employeId;
    private String employeMatricule;
    private String employeNom;

    private String siteId;
    private String siteCode;
    private String siteNom;

    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;

    private StatutAffectation statut;

    private String remplaceAffectationId;
    private String motifRemplacement;
    private String commentaire;

    /** Traçabilité de l'annulation — renseignée uniquement par {@code PlanningService.annuler}. */
    private String motifAnnulation;
    private LocalDateTime dateAnnulation;
    private String annuleParNom;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}