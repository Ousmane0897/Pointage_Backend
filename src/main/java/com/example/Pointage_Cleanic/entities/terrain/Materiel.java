package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutMateriel;
import com.example.Pointage_Cleanic.Enum.terrain.TypeMateriel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "terrain_materiel")
public class Materiel {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String nom;
    private TypeMateriel type;
    private String marque;
    private String modele;
    private String numeroSerie;

    private LocalDate dateAcquisition;
    private Long prixAcquisition;

    private String siteAffecteId;
    private String siteAffecteNom;

    private StatutMateriel statut;

    private Integer intervalleMaintenanceJours;
    private LocalDate derniereMaintenance;
    private LocalDate prochaineMaintenance;

    private String observations;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}