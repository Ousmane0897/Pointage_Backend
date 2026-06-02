package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "terrain_grilles_evaluation")
public class GrilleEvaluationTerrain {

    @Id
    private String id;

    private String nom;
    private String siteId;
    private String siteNom;

    @Builder.Default
    private List<CritereEvaluationTerrain> criteres = new ArrayList<>();

    private Double noteSeuilConformite;
    private boolean actif;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}