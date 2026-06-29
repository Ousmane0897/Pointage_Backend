package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MesureCroise;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Tableau croisé dynamique (7.5 — /analyse/croise). 2D si axeColonnes présent, sinon 1D
 * (entetesColonnes vide, valeurs vides, total renseigné par ligne).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResultatCroiseDto {

    private MesureCroise mesure;
    private List<String> entetesColonnes;
    private List<LigneCroise> lignes;
    private List<Double> totauxColonnes;
    private double totalGeneral;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LigneCroise {
        private String libelle;
        private List<Double> valeurs;
        private double total;
    }
}
