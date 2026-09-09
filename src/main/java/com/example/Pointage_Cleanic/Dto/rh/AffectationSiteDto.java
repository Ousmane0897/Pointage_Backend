package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;

/**
 * DTO d'une affectation site : tranche horaire, période de présence et semaine
 * ouvrée (voir {@link com.example.Pointage_Cleanic.entities.rh.AffectationSite}).
 * Horaires au format {@code "HH:mm"}, dates au format {@code "yyyy-MM-dd"} — toutes
 * optionnelles.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AffectationSiteDto {

    /**
     * Identité stable de la ligne, générée serveur. Le client la renvoie telle quelle ;
     * une ligne nouvelle arrive sans id et s'en voit attribuer un à l'enregistrement.
     */
    private String id;

    private String site;
    private String horaireDebut;
    private String horaireFin;

    /** Arrivée de l'employé SUR CE SITE (≠ date d'embauche dans l'entreprise). */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEntree;

    /** Départ de ce site. Absente ⇒ l'employé y est toujours en poste. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateSortie;

    /** Semaine ouvrée propre à ce site : LUN_VEN, LUN_SAM ou LUN_DIM. */
    private String joursTravail;
}
