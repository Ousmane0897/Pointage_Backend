package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.DecisionControle;
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
@Document(collection = "production_chimie_controles_qualite")
public class ControleQualite {

    @Id
    private String id;

    private String lotId;
    private String lotNumero;
    private String produitNom;
    private String grilleId;

    private LocalDateTime dateControle;
    private String controleurId;
    private String controleurNom;

    @Builder.Default
    private List<MesureControle> mesures = new ArrayList<>();

    private DecisionControle decision;
    private String commentaire;

    @Builder.Default
    private List<PhotoControle> photos = new ArrayList<>();

    private LocalDateTime createdAt;
}
