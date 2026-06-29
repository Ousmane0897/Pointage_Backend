package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeRapportConsommation;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Rapport de consommation agrégé selon un axe (site / produit / période). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RapportConsommationDto {
    private TypeRapportConsommation type;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private String siteNom;
    private String produitLibelle;
    private List<LigneRapportDto> lignes;
    private double quantiteTotale;
    private long montantTotal;
    private long nbMouvementsTotal;
    private double coutMoyenParMouvement;
}
