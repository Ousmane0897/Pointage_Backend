package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.Enum.UniteChimie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisponibiliteOf {

    private String formulationId;
    private Double quantiteCible;
    private UniteChimie uniteCible;
    private boolean globalementDisponible;
    private List<IngredientDisponibilite> ingredients;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientDisponibilite {
        private String matierePremiereId;
        private String matierePremiereNom;
        private Double quantiteRequise;
        private Double quantiteEnStock;
        private boolean suffisant;
        private Double manquant;
    }
}