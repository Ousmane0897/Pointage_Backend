package com.example.Pointage_Cleanic.entities.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Coordonnées GPS d'un site. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoordonneesGps {
    private Double latitude;
    private Double longitude;
}