package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.NiveauEscalade;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAlerte;
import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.EscaladeEntry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlerteTerrainDto {

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

    private List<EscaladeEntry> historiqueEscalade;

    private String destinataireActuelId;
    private String destinataireActuelNom;

    private String commentaire;

    private String resoluParId;
    private String resoluParNom;
    private LocalDateTime dateResolution;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}