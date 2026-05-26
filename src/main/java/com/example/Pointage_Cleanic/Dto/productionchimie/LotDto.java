package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.Enum.StatutControleLot;
import com.example.Pointage_Cleanic.Enum.StatutStockLot;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LotDto {

    private String id;
    private String numero;

    private String ordreFabricationId;
    private String ordreFabricationNumero;

    private String produitNom;
    private String formulationId;
    private String formulationCode;
    private Integer formulationVersion;

    private LocalDateTime dateFabrication;
    private LocalDate datePeremption;

    private Double quantiteProduite;
    private UniteChimie uniteProduite;

    private StatutControleLot statutControle;
    private String controleQualiteId;

    private StatutStockLot statutStock;
    private String commentaire;
    private LocalDateTime createdAt;
}
