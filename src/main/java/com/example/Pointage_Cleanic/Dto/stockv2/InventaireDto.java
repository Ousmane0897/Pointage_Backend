package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventaireDto {

    private String id;
    /** Nul en brouillon depuis le lot 1 / 1b (D4) : le numéro est attribué à la clôture. */
    private String reference;
    /** Numéro à afficher : la référence, sinon {@code BROUILLON-<6 derniers caractères de l'id>}. */
    private String libelleNumero;
    /** Annulation d'un inventaire clôturé (qui, quand, motif) ; nul sinon. */
    private com.example.Pointage_Cleanic.entities.pieces.Annulation annulation;
    /** Abandon d'un brouillon (qui, quand) ; nul sinon. */
    private com.example.Pointage_Cleanic.entities.pieces.Abandon abandon;
    private String libelle;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate datePlanifiee;

    private String siteId;
    private String siteNom;
    private PerimetreInventaire perimetre;
    private String categorieId;
    private double seuilEcartJustification;
    private StatutInventaire statut;
    private List<LigneInventaireDto> lignes;
    private String responsable;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCloture;

    private String commentaire;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
