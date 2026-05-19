package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.Enum.StatutFormulation;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.example.Pointage_Cleanic.entities.productionchimie.EtapeFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.IngredientFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.VersionFormulation;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class FicheFormulationDto {

    private String id;

    @NotBlank
    private String code;

    @NotBlank
    private String nom;

    private String description;

    private Integer versionCourante;

    @NotEmpty
    private List<IngredientFormulation> ingredients;

    @NotEmpty
    private List<EtapeFormulation> etapes;

    @NotNull
    @Positive
    private Integer dureePeremptionJours;

    @NotNull
    private UniteChimie uniteProduction;

    private StatutFormulation statut;

    private List<VersionFormulation> versions;

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    private String motif;
}