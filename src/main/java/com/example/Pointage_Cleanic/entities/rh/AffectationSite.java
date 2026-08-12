package com.example.Pointage_Cleanic.entities.rh;

import lombok.*;

/**
 * Affectation d'un employé à un site, avec une tranche horaire de travail
 * optionnelle. Sous-document embarqué dans {@link DossierEmploye} (calqué sur
 * {@link ContactUrgence}).
 * <p>
 * Les horaires sont stockés au format {@code "HH:mm"} (ex. « 06:00 »). Ils sont
 * facultatifs : {@code horaireDebut} et {@code horaireFin} peuvent être null.
 * Lorsqu'ils sont tous deux présents, {@code horaireDebut} doit être
 * strictement antérieur à {@code horaireFin} (validé côté service).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffectationSite {

    private String site;
    private String horaireDebut;
    private String horaireFin;
}
