package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_inventaires")
public class Inventaire {

    @Id
    private String id;

    @Indexed(unique = true)
    private String reference;

    private String libelle;
    private LocalDate datePlanifiee;

    @Indexed
    private String siteId;
    private String siteNom;

    private PerimetreInventaire perimetre;
    private String categorieId;

    private double seuilEcartJustification;

    @Indexed
    private StatutInventaire statut;

    @Builder.Default
    private List<LigneInventaire> lignes = new ArrayList<>();

    private String responsable;
    private LocalDate dateCloture;
    private String commentaire;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
