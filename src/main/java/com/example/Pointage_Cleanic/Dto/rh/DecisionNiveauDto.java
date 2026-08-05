package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDateTime;

/** Décision rendue à un niveau du circuit de validation des congés. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DecisionNiveauDto {

    private String decideurId;
    private String decideurNom;
    private LocalDateTime date;
    private String commentaire;
}
