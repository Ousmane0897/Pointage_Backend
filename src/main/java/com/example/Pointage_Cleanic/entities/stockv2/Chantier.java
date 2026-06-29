package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.StatutChantier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Chantier (7.5). Seule entité persistée du sous-module : sert de cible aux bons de sortie
 * {@code DISTRIBUTION_CHANTIER}. Le coût et le nombre de mouvements sont dénormalisés à la lecture
 * (agrégés depuis les mouvements de sortie rattachés).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_chantiers")
public class Chantier {

    @Id
    private String id;

    @Indexed(unique = true)
    private String reference;

    private String nom;

    private String siteId;
    private String siteNom;

    private String client;
    private String description;

    private LocalDate dateDebut;
    private LocalDate dateFin;

    @Builder.Default
    private StatutChantier statut = StatutChantier.EN_COURS;

    /** Dénormalisé en lecture : somme des montants des sorties rattachées (FCFA). */
    private Long coutTotal;
    /** Dénormalisé en lecture : nombre de lignes de sortie rattachées. */
    private Integer nbMouvements;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
