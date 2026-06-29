package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HistoriqueCoutProduitDto {
    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private List<PointDto> points;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PointDto {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate date;
        private long cout;
        private MethodeValorisation methode;
        private String reference;
    }
}
