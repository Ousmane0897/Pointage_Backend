package com.example.Pointage_Cleanic.Dto.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KpiProductionPeriode {
    private LocalDate dateDebut;
    private LocalDate dateFin;
    private double volumeProduitLitres;
    private long nbOfTermines;
    private long nbOfAnnules;
    private double tauxReussiteCq;
    private double tauxPerteMoyen;
    private long nbLotsValide;
    private long nbLotsRejete;
    private long nbLotsEnAttenteControle;
    private long nbLotsTotaux;
}
