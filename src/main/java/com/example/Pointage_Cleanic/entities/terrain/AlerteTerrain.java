package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.NiveauEscalade;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAlerte;
import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteTerrain;
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
@Document(collection = "terrain_alertes")
public class AlerteTerrain {

    @Id
    private String id;

    private TypeAlerteTerrain type;
    private NiveauEscalade niveauActuel;
    private StatutAlerte statut;

    private String employeId;
    private String employeMatricule;
    private String employeNom;

    private String siteId;
    private String siteNom;

    private String affectationId;
    private String pointageId;

    private LocalDateTime dateEvenement;
    private LocalDateTime dateDetection;

    @Builder.Default
    private List<EscaladeEntry> historiqueEscalade = new ArrayList<>();

    private String destinataireActuelId;
    private String destinataireActuelNom;

    private String commentaire;

    private String resoluParId;
    private String resoluParNom;
    private LocalDateTime dateResolution;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}