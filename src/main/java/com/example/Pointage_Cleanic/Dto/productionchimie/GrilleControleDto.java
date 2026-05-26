package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.TestControle;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class GrilleControleDto {

    private String id;

    @NotBlank
    private String produitNom;

    private String formulationId;

    @NotEmpty
    private List<TestControle> tests;

    private boolean actif;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
