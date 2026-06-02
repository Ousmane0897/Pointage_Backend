package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.CategoriePhyto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "produits_phyto")
public class ProduitPhytosanitaire {

    @Id
    private String id;

    private String nomCommercial;
    private String matiereActive;
    private CategoriePhyto categorie;

    @Indexed(unique = true)
    private String numeroHomologation;

    private String doseRecommandee;
    private Integer delaiReentreeHeures;
    private Integer delaiAvantNouvelleApplicationJours;
    private String fournisseur;
    private String fdsUrl;

    private boolean actif;

    private LocalDateTime createdAt;
}