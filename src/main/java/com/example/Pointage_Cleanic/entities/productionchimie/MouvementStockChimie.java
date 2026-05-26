package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.TypeMouvementChimie;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "production_chimie_mouvements_stock")
public class MouvementStockChimie {

    @Id
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