package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Statistique d'usage d'une catégorie (TypeEntree ou TypeSortie). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatistiqueCategorieDto {
    private String code;
    private String libelle;
    private long nombre;
    private double volume;
    private long montant;
    private double pourcentage;
}
