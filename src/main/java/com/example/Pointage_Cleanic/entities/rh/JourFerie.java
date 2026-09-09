package com.example.Pointage_Cleanic.entities.rh;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un jour férié du calendrier de l'entreprise.
 *
 * <p>Référentiel <b>saisi par la RH, année par année</b>, et non dérivé d'une liste en dur :
 * au Sénégal, les fêtes musulmanes (Korité, Tabaski, Tamkharit, Maouloud) sont mobiles et
 * annoncées tardivement — aucune règle de calcul ne peut les produire à l'avance. C'est le
 * remplaçant du référentiel supprimé au commit {@code a31c2f5}, sous une route neuve du module
 * Temps &amp; Présences ({@code /api/temps-presences/jours-feries}) : l'ancien {@code /api/ferie}
 * n'est pas ressuscité.
 *
 * <p>⚠ <b>Un jour absent de cette collection n'est pas férié.</b> C'est la seule source de
 * vérité du calcul des jours ouvrables ({@code CalendrierTravailService}), du décompte des
 * congés et du pointage centralisé. Une base vide reproduit donc exactement le comportement
 * antérieur, ce qui rend le déploiement sans effet tant que la RH n'a rien saisi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "rh_jours_feries")
public class JourFerie {

    @Id
    private String id;

    /**
     * Date du férié. <b>Unique</b> — deux libellés ne peuvent pas se disputer le même jour,
     * sans quoi le même jour serait décompté deux fois par toute agrégation qui compterait
     * les documents plutôt que les dates distinctes.
     */
    @Indexed(unique = true)
    private LocalDate date;

    /** Libellé affiché : « Fête de l'Indépendance », « Korité », … */
    private String libelle;

    /**
     * Jour <b>chômé</b> (l'entreprise ne travaille pas). {@code false} décrit un férié légal
     * que l'entreprise décide de travailler.
     *
     * <p>⚠ Dans les deux cas le jour <b>sort des jours ouvrables</b> : c'est la décision
     * métier arbitrée — un férié n'est jamais dû, et un agent qui ne vient pas n'est pas en
     * absence. La différence porte sur le travail réellement effectué, qui reste compté par
     * le pointage. Ce drapeau est donc <b>informatif</b> pour la RH ; ne pas s'en servir pour
     * réintroduire le jour dans les ouvrables, ce serait rouvrir le bug que ce lot corrige.
     */
    @Builder.Default
    private Boolean chome = true;

    /**
     * Férié à <b>date fixe</b>, reconductible d'une année sur l'autre (1er janvier, Fête du
     * Travail, Indépendance, Noël…). Les fêtes mobiles valent {@code false}.
     *
     * <p>⚠ Ce n'est qu'une <b>aide à la saisie</b>, consommée par la duplication d'année :
     * aucun calcul ne le lit. Le calendrier ne raisonne que sur des dates réellement
     * enregistrées — un férié « récurrent » non dupliqué n'existe pas pour l'année suivante.
     */
    @Builder.Default
    private Boolean recurrent = false;

    // --- Métadonnées de modification (mêmes champs que ParametresConges) ---

    private LocalDateTime dateModification;
    private String modifieParId;
    private String modifieParNom;
}
