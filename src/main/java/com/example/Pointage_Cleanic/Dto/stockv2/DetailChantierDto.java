package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Détail agrégé d'un chantier : entête + lignes de consommation + totaux. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DetailChantierDto {
    private ChantierDto chantier;
    private List<LigneConsommationChantierDto> lignes;
    private long coutTotal;
    private int nbProduits;
    private Integer dureeJours;
}
