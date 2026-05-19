package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.StatutControleLot;
import com.example.Pointage_Cleanic.Enum.StatutStockLot;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "production_chimie_lots")
public class Lot {

    @Id
    private String id;

    @Indexed(unique = true)
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