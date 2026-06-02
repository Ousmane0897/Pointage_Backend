package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutPointage;
import com.example.Pointage_Cleanic.Enum.terrain.TypePointage;
import com.example.Pointage_Cleanic.entities.terrain.PositionGps;
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
public class PointageTerrainDto {

    private String id;

    @NotBlank
    private String employeId;
    private String employeMatricule;
    private String employeNom;

    @NotBlank
    private String siteId;
    private String siteCode;
    private String siteNom;

    private String affectationId;

    @NotNull
    private TypePointage type;

    private LocalDateTime datePointage;

    @NotNull
    private PositionGps positionAgent;

    private Double distanceM;
    private StatutPointage statut;

    private String commentaire;

    private LocalDateTime createdAt;
}