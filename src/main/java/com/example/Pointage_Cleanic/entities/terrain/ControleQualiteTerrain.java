package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.DecisionControleTerrain;
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
@Document(collection = "terrain_controles")
public class ControleQualiteTerrain {

    @Id
    private String id;

    private String siteId;
    private String siteCode;
    private String siteNom;

    private String grilleId;
    private String grilleNom;

    private LocalDateTime dateControle;

    private String controleurEmployeId;
    private String controleurNom;

    @Builder.Default
    private List<NotationCritere> notations = new ArrayList<>();

    private Double noteGlobale;
    private DecisionControleTerrain decision;
    private String commentaire;

    @Builder.Default
    private List<PhotoControleFichier> photos = new ArrayList<>();

    private LocalDateTime createdAt;
}