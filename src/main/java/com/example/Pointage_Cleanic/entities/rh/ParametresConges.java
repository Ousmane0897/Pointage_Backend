package com.example.Pointage_Cleanic.entities.rh;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Barème des droits à congés — document <b>singleton</b> (get-or-create paresseux,
 * comme le paramétrage de production chimie et celui d'escalade terrain).
 *
 * <p>Il porte trois règles, toutes issues du droit du travail sénégalais mais rendues
 * modifiables pour qu'une évolution réglementaire n'exige pas un déploiement :
 * <ol>
 *   <li>l'acquis de base — 2 jours ouvrables par mois de service effectif ;</li>
 *   <li>+1 jour par enfant de moins de 14 ans, pour les mères de famille ;</li>
 *   <li>l'ancienneté — +1 j à 10 ans, +2 à 15, +3 à 20, +6 à 25.</li>
 * </ol>
 *
 * <p>⚠ <b>Limite assumée : le barème n'est pas versionné par date d'effet.</b> Le modifier
 * recalcule aussi les exercices clos, donc le reliquat reporté des années antérieures.
 * C'est cohérent — le barème est censé refléter la règle applicable — mais cela signifie
 * qu'un changement fait bouger des soldes déjà consultés. L'écran de paramétrage
 * l'annonce explicitement avant l'enregistrement.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "parametres_conges")
public class ParametresConges {

    @Id
    private String id;

    // --- Acquis de base ---

    /** Jours ouvrables acquis par mois de service effectif (2 = valeur légale). */
    private Integer joursAcquisParMois;

    // --- Supplément pour enfants à charge ---

    private Boolean supplementEnfantsActif;

    /** Jours accordés par enfant éligible. */
    private Integer joursParEnfant;

    /** Âge limite, <b>exclu</b> : « moins de 14 ans » ⇒ 14. */
    private Integer ageMaxEnfant;

    /**
     * Réserve le supplément aux femmes, conformément à la formulation « mères de
     * famille ». Modifiable pour pouvoir l'ouvrir aux deux parents sans redéploiement.
     */
    private Boolean reserverAuxMeres;

    /** Plafond de jours au titre des enfants. {@code null} ⇒ aucun plafond. */
    private Integer plafondJoursEnfants;

    // --- Supplément d'ancienneté ---

    private Boolean supplementAncienneteActif;

    /** Paliers non cumulatifs — cf. {@link PalierAncienneteConge}. */
    private List<PalierAncienneteConge> paliersAnciennete;

    // --- Divers ---

    /**
     * Proratise les suppléments sur les mois de service de l'exercice. {@code false} par
     * défaut : le texte énonce des jours entiers. Interrupteur prévu pour ne pas avoir à
     * migrer si l'arbitrage change.
     */
    private Boolean proratiserSupplements;

    // --- Métadonnées de modification (mêmes champs que ParametresPaie) ---

    private LocalDateTime dateModification;
    private String modifieParId;
    private String modifieParNom;
}
