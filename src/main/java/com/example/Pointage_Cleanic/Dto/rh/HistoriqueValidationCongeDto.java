package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.ActionValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

/** Entrée d'historique du circuit de validation d'une demande de congé. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoriqueValidationCongeDto {

    private ActionValidationConge action;
    private NiveauValidationConge niveau;
    private String auteurId;
    private String auteurNom;
    private LocalDateTime date;
    private String commentaire;
}
