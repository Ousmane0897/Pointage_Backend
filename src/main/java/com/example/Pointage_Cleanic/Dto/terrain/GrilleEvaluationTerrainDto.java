package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.entities.terrain.CritereEvaluationTerrain;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
public class GrilleEvaluationTerrainDto {

    private String id;

    @NotBlank
    private String nom;

    private String siteId;
    private String siteNom;

    private List<CritereEvaluationTerrain> criteres;

    private Double noteSeuilConformite;
    private boolean actif;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}