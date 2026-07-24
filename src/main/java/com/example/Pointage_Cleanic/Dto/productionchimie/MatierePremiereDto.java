package com.example.Pointage_Cleanic.Dto.productionchimie;

import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatierePremiereDto {

    private String id;

    @NotBlank
    private String code;

    @NotBlank
    private String nom;

    @NotNull
    private UniteChimie unite;

    @NotNull
    @PositiveOrZero
    private Double seuilCritique;

    private String fournisseur;

    /** Concentration en matière active (%, 0–100). Nombre, jamais texte. Null si non renseignée. */
    @PositiveOrZero
    @DecimalMax("100.0")
    private Double matiereActivePct;

    /** La MP entre-t-elle dans le calcul de la matière active d'une formule (Fonction A). */
    private boolean compterDansMa;

    private String ficheSecuriteUrl;
    private String ficheSecuriteNom;

    @PositiveOrZero
    private Double quantiteEnStock;

    private boolean actif;
    private String remarque;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}