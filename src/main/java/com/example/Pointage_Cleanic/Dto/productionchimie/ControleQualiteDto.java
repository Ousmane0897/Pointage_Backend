package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.Enum.DecisionControle;
import com.example.Pointage_Cleanic.entities.productionchimie.MesureControle;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ControleQualiteDto {

    private String id;

    @NotBlank
    private String lotId;
    private String lotNumero;
    private String produitNom;
    private String grilleId;

    @NotNull
    private LocalDateTime dateControle;

    private String controleurId;
    private String controleurNom;

    private List<MesureControle> mesures;

    @NotNull
    private DecisionControle decision;

    private String commentaire;

    private List<PhotoControleDto> photos;

    private LocalDateTime createdAt;
}
