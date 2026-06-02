package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Compteur quotidien atomique pour la numérotation des interventions (INT-) et
 * des applications phytosanitaires (PHYTO-). Un document par préfixe et par jour,
 * {@code _id = "INT-yyyyMMdd"} ou {@code "PHYTO-yyyyMMdd"}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "compteurs_terrain")
public class CompteurTerrain {

    @Id
    private String id;

    private long compteur;
}