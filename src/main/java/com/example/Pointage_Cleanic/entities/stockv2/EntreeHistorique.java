package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Entrée de l'historique de workflow d'un bon. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntreeHistorique {
    private ActionWorkflow action;
    private String auteur;
    private LocalDateTime date;
    private String commentaire;
}
