package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.Enum.TypeMouvementChimie;
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
public class MouvementStockChimieDto {

    private String id;
    private String matierePremiereId;
    private String matierePremiereCode;
    private String matierePremiereNom;
    private UniteChimie unite;

    private TypeMouvementChimie type;
    private Double quantite;
    private LocalDateTime date;

    private String ordreFabricationId;
    private String ordreFabricationNumero;

    private String lotFournisseur;
    private String fournisseur;
    private LocalDate datePeremption;

    private String commentaire;
    private String auteurId;
    private String auteurNom;
}