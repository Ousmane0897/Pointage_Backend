package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventaireDto {

    private String id;
    private String reference;
    private String libelle;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate datePlanifiee;

    private String siteId;
    private String siteNom;
    private PerimetreInventaire perimetre;
    private String categorieId;
    private double seuilEcartJustification;
    private StatutInventaire statut;
    private List<LigneInventaireDto> lignes;
    private String responsable;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCloture;

    private String commentaire;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
