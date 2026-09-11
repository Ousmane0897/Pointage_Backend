package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * Semaine ouvrée propre à ce site : LUN_VEN, LUN_SAM, LUN_DIM ou PERSONNALISE.
     * {@code PERSONNALISE} n'est pas un rythme mais un marqueur désignant {@link #joursSemaine}.
     */
    private String joursTravail;

    /**
     * Jour de repos hebdomadaire, en indice {@code Date.getDay()} (0 = dimanche … 6 = samedi).
     * Optionnel — null ⇒ repos implicite du dimanche, et le champ ne vaut que pour les
     * rythmes de plus de cinq jours. Voir
     * {@link com.example.Pointage_Cleanic.entities.rh.AffectationSite} pour la règle complète.
     */
    private Integer jourRepos;

    /**
     * Jours travaillés explicites sur ce site, en indices {@code Date.getDay()}
     * (0 = dimanche … 6 = samedi). <b>Non vide ⇒ fait seule autorité</b>, ni
     * {@code joursTravail} ni {@code jourRepos} ne s'y appliquent ; null ou vide ⇒
     * comportement antérieur inchangé. Voir
     * {@link com.example.Pointage_Cleanic.entities.rh.AffectationSite} pour la règle complète.
     */
    private List<Integer> joursSemaine;
}
