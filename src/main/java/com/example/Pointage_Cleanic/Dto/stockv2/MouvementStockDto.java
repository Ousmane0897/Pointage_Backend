package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MouvementStockDto {

    private String id;
    private String reference;
    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private UniteStock unite;
    private TypeMouvement type;
    private MotifMouvement motif;
    private double quantite;
    private String siteSourceId;
    private String siteSourceNom;
    private String siteDestinationId;
    private String siteDestinationNom;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String utilisateur;
    private String commentaire;
    private LocalDateTime createdAt;

    // --- 7.4 : traçabilité du bon source (null pour les mouvements directs) ---
    private String origine;
    private String bonId;
    private String bonReference;
    private TypeEntree categorieEntree;
    private TypeSortie categorieSortie;
}
