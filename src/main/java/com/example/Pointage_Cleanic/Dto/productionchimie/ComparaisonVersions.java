package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.EtapeFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.IngredientFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.VersionFormulation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparaisonVersions {

    private Integer v1;
    private Integer v2;
    private VersionFormulation version1;
    private VersionFormulation version2;

    private DiffIngredients diffIngredients;
    private DiffEtapes diffEtapes;
    private DiffPeremption diffPeremption;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffIngredients {
        private List<IngredientFormulation> added;
        private List<IngredientFormulation> removed;
        private List<IngredientFormulation> modified;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffEtapes {
        private List<EtapeFormulation> added;
        private List<EtapeFormulation> removed;
        private List<EtapeFormulation> modified;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiffPeremption {
        private Integer oldValue;
        private Integer newValue;
    }
}