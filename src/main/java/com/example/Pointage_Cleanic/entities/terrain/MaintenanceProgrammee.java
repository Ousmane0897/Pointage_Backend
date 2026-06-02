package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.TypeMaintenance;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "terrain_maintenances_programmees")
public class MaintenanceProgrammee {

    @Id
    private String id;

    private String materielId;
    private String materielNom;

    private LocalDate dateProgrammee;
    private TypeMaintenance type;
    private String description;

    private boolean realisee;
    private LocalDate dateRealisation;

    private String technicienEmployeId;
    private String commentaire;
}