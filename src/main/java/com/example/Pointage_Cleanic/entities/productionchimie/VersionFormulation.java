package com.example.Pointage_Cleanic.entities.productionchimie;

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
public class VersionFormulation {
    private Integer numero;
    private LocalDateTime dateModification;
    private String auteur;
    private String motif;
    private List<IngredientFormulation> ingredients;
    private List<EtapeFormulation> etapes;
    private Integer dureePeremptionJours;
}