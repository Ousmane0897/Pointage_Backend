package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Notation d'un critère lors d'un contrôle qualité. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotationCritere {
    private String parametre;
    private String libelle;
    private Double poids;
    private Double note;
    private String commentaire;
}