package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.DecisionControleTerrain;
import com.example.Pointage_Cleanic.entities.terrain.NotationCritere;
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
public class ControleQualiteTerrainDto {

    private String id;

    @NotBlank
    private String siteId;
    private String siteCode;
    private String siteNom;

    private String grilleId;
    private String grilleNom;

    private LocalDateTime dateControle;

    private String controleurEmployeId;
    private String controleurNom;

    private List<NotationCritere> notations;

    private Double noteGlobale;
    private DecisionControleTerrain decision;
    private String commentaire;

    private List<PhotoControleTerrainDto> photos;

    private LocalDateTime createdAt;
}