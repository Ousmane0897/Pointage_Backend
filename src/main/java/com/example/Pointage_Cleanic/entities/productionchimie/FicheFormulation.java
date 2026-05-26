package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.StatutFormulation;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "production_chimie_formulations")
public class FicheFormulation {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String nom;
    private String description;

    private Integer versionCourante;

    private List<IngredientFormulation> ingredients;
    private List<EtapeFormulation> etapes;
    private Integer dureePeremptionJours;
    private Double quantiteRef;
    private UniteChimie uniteProduction;

    private StatutFormulation statut;

    @Builder.Default
    private List<VersionFormulation> versions = new ArrayList<>();

    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}