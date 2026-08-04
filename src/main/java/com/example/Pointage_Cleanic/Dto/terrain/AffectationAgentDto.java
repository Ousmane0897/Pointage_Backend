package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AffectationAgentDto {

    private String id;

    @NotBlank
    private String employeId;
    private String employeMatricule;
    private String employeNom;

    @NotBlank
    private String siteId;
    private String siteCode;
    private String siteNom;

    @NotNull
    private LocalDateTime dateDebut;

    /** Optionnelle : absente = affectation à durée indéterminée. */
    private LocalDateTime dateFin;

    private StatutAffectation statut;

    private String remplaceAffectationId;
    private String motifRemplacement;
    private String commentaire;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}