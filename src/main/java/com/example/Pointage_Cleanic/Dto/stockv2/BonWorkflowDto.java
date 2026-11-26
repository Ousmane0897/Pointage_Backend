package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.SensBon;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Carte de la vue Kanban unifiée (entrées + sorties). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BonWorkflowDto {
    private String id;
    /** Nul en brouillon depuis le lot 1 / 1b (D4). */
    private String reference;
    /** Numéro à afficher : la référence, sinon {@code BROUILLON-<6 derniers caractères de l'id>}. */
    private String libelleNumero;
    private SensBon sens;
    private StatutBon statut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String libelleType;
    private String siteNom;
    private String destinataireNom;
    private String demandeurNom;
    private String validateurNom;
    private int nbLignes;
    private long montantTotal;
    private String motifRefus;
}
