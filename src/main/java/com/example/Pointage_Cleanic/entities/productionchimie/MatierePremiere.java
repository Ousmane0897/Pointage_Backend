package com.example.Pointage_Cleanic.entities.productionchimie;

import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "production_chimie_matieres_premieres")
public class MatierePremiere {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String nom;
    private UniteChimie unite;
    private Double seuilCritique;
    private String fournisseur;

    /** Concentration en matière active (%, 0–100). Nombre, jamais texte. Null si non renseignée. */
    private Double matiereActivePct;
    /** La MP entre-t-elle dans le calcul de la matière active d'une formule (Fonction A). */
    private boolean compterDansMa;

    @JsonIgnore
    private byte[] ficheSecurite;
    private String ficheSecuriteNom;
    private String ficheSecuriteMimeType;
    private Long ficheSecuriteTaille;

    private Double quantiteEnStock;
    private boolean actif;
    private String remarque;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}