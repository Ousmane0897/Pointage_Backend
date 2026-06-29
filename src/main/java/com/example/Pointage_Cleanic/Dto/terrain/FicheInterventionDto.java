package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutIntervention;
import com.example.Pointage_Cleanic.entities.terrain.ProduitUtilise;
import com.example.Pointage_Cleanic.entities.terrain.SignatureClient;
import com.example.Pointage_Cleanic.entities.terrain.TacheChecklist;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FicheInterventionDto {

    private String id;
    private String numero;

    @NotBlank
    private String siteId;
    private String siteCode;
    private String siteNom;

    private String affectationId;

    @NotBlank
    private String employeId;
    private String employeMatricule;
    private String employeNom;

    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private Integer duree;

    private List<TacheChecklist> taches;
    private List<ProduitUtilise> produits;

    private String observations;

    private List<PhotoInterventionDto> photos;

    private SignatureClient signatureClient;

    private StatutIntervention statut;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Champs de pilotage multipart (entrée uniquement) ──
    /** Métadonnées des nouvelles photos, alignées sur l'ordre des fichiers uploadés. */
    private List<PhotoMetaDto> photosMeta;
    /** Indices des photos existantes à conserver (édition). */
    private List<Integer> photosConservees;
}