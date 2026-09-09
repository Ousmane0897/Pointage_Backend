package com.example.Pointage_Cleanic.Dto.rh;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Jour férié — contrat d'échange avec le front.
 *
 * <p>{@code date} et {@code libelle} sont requis : un férié sans date ne veut rien dire, et
 * un férié sans libellé serait inexploitable dans l'écran de saisie comme dans les exports.
 * Les métadonnées de modification sont renseignées <b>serveur</b> depuis le JWT et jamais
 * acceptées du client (même règle que {@code ParametresConges}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourFerieDto {

    private String id;

    @NotNull(message = "La date du jour férié est obligatoire")
    private LocalDate date;

    @NotBlank(message = "Le libellé du jour férié est obligatoire")
    private String libelle;

    /** Défaut serveur {@code true} si absent — cf. {@code JourFerieService}. */
    private Boolean chome;

    /** Défaut serveur {@code false} si absent — aide à la saisie, jamais une règle de calcul. */
    private Boolean recurrent;

    private LocalDateTime dateModification;
    private String modifieParId;
    private String modifieParNom;
}
