package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutPointage;
import com.example.Pointage_Cleanic.Enum.terrain.TypePointage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "terrain_pointages")
public class PointageTerrain {

    @Id
    private String id;

    private String employeId;
    private String employeMatricule;
    private String employeNom;

    private String siteId;
    private String siteCode;
    private String siteNom;

    private String affectationId;

    private TypePointage type;
    private LocalDateTime datePointage;

    private PositionGps positionAgent;
    private Double distanceM;
    private StatutPointage statut;

    private String commentaire;

    private LocalDateTime createdAt;
}