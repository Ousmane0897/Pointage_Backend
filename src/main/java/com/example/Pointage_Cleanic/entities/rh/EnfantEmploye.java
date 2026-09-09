package com.example.Pointage_Cleanic.entities.rh;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

/**
 * Enfant à charge d'un employé. Sous-document embarqué dans {@link DossierEmploye},
 * calqué sur {@link ContactUrgence} et {@link AffectationSite}.
 *
 * <p><b>Pourquoi une liste datée et non un simple compteur.</b> Le droit à congé
 * supplémentaire (« 1 jour par enfant de moins de 14 ans pour les mères de famille »)
 * dépend de l'<i>âge</i> de chaque enfant à la date de référence de l'exercice. Un
 * compteur scalaire ne permet ni de trancher cette condition, ni surtout de recalculer
 * à l'identique un exercice clos : il ferait bouger le passé à chaque anniversaire, et
 * donc bouger le reliquat de congés déjà reporté. La date de naissance, elle, est
 * immuable — c'est ce qui rend le calcul déterministe. Cf.
 * {@link com.example.Pointage_Cleanic.services.rh.CongeAcquisCalculator}.
 *
 * <p>{@code DossierEmploye.nombreEnfants} reste le champ historique : il est
 * <b>dérivé</b> de cette liste dès qu'elle est renseignée, et conservé tel quel pour
 * les dossiers antérieurs qui ne portent que lui.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnfantEmploye {

    /**
     * Identité stable de la ligne. La liste étant remplacée en bloc à chaque écriture,
     * c'est le seul moyen pour le client de reconnaître une ligne déjà persistée.
     * Posé serveur par {@link com.example.Pointage_Cleanic.util.EnfantEmployeUtils}.
     */
    private String id;

    private String prenom;

    /** Date de naissance — la donnée qui ouvre (ou non) le droit supplémentaire. */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateNaissance;
}
