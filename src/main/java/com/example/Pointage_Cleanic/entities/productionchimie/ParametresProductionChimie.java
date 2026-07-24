package com.example.Pointage_Cleanic.entities.productionchimie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Paramètres globaux du module Production Chimie (document singleton).
 *
 * <p>Porte la tolérance de contrôle du total d'une formulation (Fonction C).
 * Suit le pattern des paramétrages existants ({@code terrain_parametres_escalade},
 * {@code ParametresPaie}).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "production_chimie_parametres")
public class ParametresProductionChimie {

    @Id
    private String id;

    /** Tolérance (± %) entre la somme des dosages saisis et la taille du lot. Défaut 0,1 %. */
    private Double toleranceTotalPct;

    private LocalDateTime updatedAt;
    private String updatedBy;
}
