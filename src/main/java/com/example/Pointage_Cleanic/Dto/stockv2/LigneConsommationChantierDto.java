package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Ligne de consommation d'un chantier, agrégée par produit (sorties EFFECTIVES rattachées). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LigneConsommationChantierDto {
    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private UniteStock unite;
    private double quantite;
    private Long prixUnitaire;
    private long montant;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate premiereDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate derniereDate;
}
