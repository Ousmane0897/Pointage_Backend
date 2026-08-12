package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.Map;

/** Compteurs de la file de validation de l'appelant, par niveau (onglets du front). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompteursAValiderDto {

    private long total;

    /** Toujours renseigné pour les 3 niveaux, valeur 0 comprise. */
    private Map<NiveauValidationConge, Long> parNiveau;
}
