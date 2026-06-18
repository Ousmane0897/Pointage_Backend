package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Comparatif dotation prévue (plafonds) vs réelle (sorties effectives) sur un mois. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComparatifDotationDto {
    private String mois;
    private List<LigneComparatifDto> lignes;
    private double totalPrevu;
    private double totalReel;
}
