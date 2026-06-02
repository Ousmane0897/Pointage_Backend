package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Zone traitée lors d'une application phytosanitaire. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneTraitement {
    private String libelle;
    private Double surfaceM2;
    private String description;
}