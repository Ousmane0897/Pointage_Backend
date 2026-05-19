package com.example.Pointage_Cleanic.Dto.productionchimie;

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
public class TendanceParametre {

    private String parametre;
    private String unite;
    private String valeurCible;
    private List<Point> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private LocalDateTime date;
        private String lotNumero;
        private Double valeur;
        private boolean conforme;
    }
}
