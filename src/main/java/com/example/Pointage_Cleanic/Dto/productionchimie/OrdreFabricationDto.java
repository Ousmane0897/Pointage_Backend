package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.Enum.StatutOf;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.example.Pointage_Cleanic.entities.productionchimie.ConsommationMp;
import com.example.Pointage_Cleanic.entities.productionchimie.HistoriqueStatutOf;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
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
public class OrdreFabricationDto {

    private String id;
    private String numero;

    @NotBlank
    private String produitNom;

    @NotBlank
    private String formulationId;
    private String formulationCode;
    private Integer formulationVersion;

    @NotNull
    @Positive
    private Double quantiteCible;

    @NotNull
    private UniteChimie uniteCible;

    private Double quantiteReelle;

    @NotNull
    private LocalDateTime dateLancementPrevue;
    private LocalDateTime dateLancementEffective;
    private LocalDateTime dateFin;

    @NotBlank
    private String operateurResponsableId;
    private String operateurResponsableNom;

    private StatutOf statut;

    private List<HistoriqueStatutOf> historiqueStatuts;
    private List<ConsommationMp> consommationMp;

    private String lotGenereId;
    private String lotGenereNumero;
    private String commentaire;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}