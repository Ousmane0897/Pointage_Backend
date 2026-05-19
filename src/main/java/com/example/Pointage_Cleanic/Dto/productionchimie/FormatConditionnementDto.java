package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.Enum.TypeContenant;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.example.Pointage_Cleanic.entities.productionchimie.DimensionsContenant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class FormatConditionnementDto {

    private String id;

    @NotBlank
    private String code;

    @NotBlank
    private String libelle;

    @NotNull
    @Positive
    private Double volume;

    @NotNull
    private UniteChimie uniteVolume;

    @NotNull
    private TypeContenant typeContenant;

    private DimensionsContenant dimensions;
    private Double poidsVide;
    private boolean actif;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}