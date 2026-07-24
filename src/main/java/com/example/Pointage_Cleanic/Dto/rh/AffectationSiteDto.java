package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

/**
 * DTO d'une affectation site + tranche horaire (voir
 * {@link com.example.Pointage_Cleanic.entities.rh.AffectationSite}).
 * Horaires au format {@code "HH:mm"}, optionnels.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AffectationSiteDto {

    private String site;
    private String horaireDebut;
    private String horaireFin;
}
