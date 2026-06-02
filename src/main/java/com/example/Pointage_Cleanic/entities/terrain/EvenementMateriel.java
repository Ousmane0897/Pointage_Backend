package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.TypeEvenementMateriel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "terrain_evenements_materiel")
public class EvenementMateriel {

    @Id
    private String id;

    private String materielId;
    private TypeEvenementMateriel type;
    private LocalDateTime date;
    private String description;

    private String siteAvantId;
    private String siteApresId;

    private Long cout;

    private String technicienEmployeId;
    private String technicienNom;

    private Double duree;
    private Boolean resolu;

    private List<String> documents;

    private LocalDateTime createdAt;
}