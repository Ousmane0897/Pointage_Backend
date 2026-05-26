package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.ConsommationMp;
import com.example.Pointage_Cleanic.entities.productionchimie.VersionFormulation;
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
public class TracabiliteLot {

    private LotDto lot;
    private OrdreFabricationResume ordreFabrication;
    private FormulationResume formulation;
    private List<ConsommationMp> consommationsMp;
    private String controleQualiteId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrdreFabricationResume {
        private String id;
        private String numero;
        private String operateurNom;
        private LocalDateTime dateLancementEffective;
        private LocalDateTime dateFin;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FormulationResume {
        private String id;
        private String code;
        private String nom;
        private VersionFormulation version;
    }
}
