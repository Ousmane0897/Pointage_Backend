package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Enum.rh.GenreEmploye;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/**
 * Droits à congés payés acquis, au sens du droit du travail sénégalais :
 * <ul>
 *   <li><b>acquis de base</b> — 2 jours ouvrables par mois de service effectif
 *       (24 jours pour une année pleine) ;</li>
 *   <li><b>+1 jour par enfant de moins de 14 ans</b>, pour les mères de famille ;</li>
 *   <li><b>ancienneté</b> — +1 j à 10 ans, +2 à 15, +3 à 20, +6 à 25 (paliers
 *       <b>non cumulatifs</b>).</li>
 * </ul>
 *
 * <p>Ce composant est volontairement <b>pur</b> : aucun accès base, aucune valeur de
 * configuration lue ici, l'« aujourd'hui » et le {@link BaremeConges} sont toujours passés
 * en paramètre. C'est ce qui permet de le tester sans contexte Spring et sur dates figées —
 * et c'est ce qui permet à l'appelant de lire le barème une seule fois pour N employés.
 *
 * <h2>Deux dates de référence, à ne pas confondre</h2>
 * <ul>
 *   <li>{@link #moisAcquis} borne l'exercice au <b>1er janvier suivant, exclu</b> :
 *       {@code MONTHS.between(2025-01-01, 2025-12-31)} vaut 11 et amputerait d'un mois
 *       toute année pleine ;</li>
 *   <li>l'âge des enfants et l'ancienneté s'apprécient au <b>31 décembre de l'exercice</b>.
 *       Ces droits sont annuels et acquis en bloc : les rattacher à « aujourd'hui » ferait
 *       recalculer les exercices clos avec l'ancienneté d'aujourd'hui, gonflant le reliquat
 *       reporté à chaque anniversaire.</li>
 * </ul>
 */
@Component
public class CongeAcquisCalculator {

    /**
     * Mois de service effectif <b>révolus</b> de l'employé sur l'exercice {@code annee}.
     *
     * <p>Le décompte est de quantième à quantième : une entrée le 15/03 vaut 5 mois au 02/09
     * (15/03 → 15/08), pas 6. C'est bien la notion de « service effectif ».
     *
     * @param dateEntree entrée dans l'entreprise ; {@code null} ⇒ l'employé est réputé présent
     *                   depuis le 1er janvier de l'exercice (repli pour les dossiers antérieurs,
     *                   où le champ n'a jamais été obligatoire)
     */
    public int moisAcquis(int annee, LocalDate dateEntree, LocalDate aujourdhui) {
        LocalDate premierJanvier = LocalDate.of(annee, 1, 1);
        LocalDate debut = (dateEntree != null && dateEntree.isAfter(premierJanvier))
                ? dateEntree
                : premierJanvier;

        LocalDate finExclue = LocalDate.of(annee + 1, 1, 1);
        LocalDate borne = aujourdhui.isBefore(finExclue) ? aujourdhui : finExclue;

        long mois = ChronoUnit.MONTHS.between(debut, borne);
        // Exercice antérieur à l'entrée, ou entrée dans le futur : aucun droit, jamais de négatif.
        return (int) Math.max(0, mois);
    }

    /** Acquis de base, hors toute majoration — cf. {@link #moisAcquis}. */
    public int acquisDeBase(int annee, LocalDate dateEntree, LocalDate aujourdhui, BaremeConges bareme) {
        return moisAcquis(annee, dateEntree, aujourdhui) * bareme.joursAcquisParMois();
    }

    /**
     * Ancienneté en années révolues au 31 décembre de l'exercice.
     * {@code 0} sans date d'entrée, ou si l'entrée est postérieure à cette date.
     */
    public int anneesAnciennete(int annee, LocalDate dateEntree) {
        if (dateEntree == null) {
            return 0;
        }
        LocalDate reference = referenceExercice(annee);
        if (dateEntree.isAfter(reference)) {
            return 0;
        }
        return (int) Math.max(0, ChronoUnit.YEARS.between(dateEntree, reference));
    }

    /**
     * Enfants ouvrant droit à la majoration sur l'exercice : nés au plus tard au 31 décembre
     * et ayant, à cette date, <b>strictement moins</b> de {@code ageMaxEnfant} ans.
     *
     * <p>L'âge se calcule par {@link Period#between} et non par une division de jours : c'est
     * ce qui rend « moins de 14 ans » exact les années bissextiles.
     *
     * <p>La réserve aux mères s'applique ici : un dossier sans genre renseigné n'est pas
     * bénéficiaire tant que la réserve est active — on n'invente pas un droit sur une donnée
     * absente.
     */
    public int enfantsBeneficiaires(int annee, DroitsEmployeSnapshot employe, BaremeConges bareme) {
        if (!bareme.supplementEnfantsActif()) {
            return 0;
        }
        if (bareme.reserverAuxMeres() && employe.genre() != GenreEmploye.FEMME) {
            return 0;
        }
        LocalDate reference = referenceExercice(annee);
        return (int) employe.naissancesEnfants().stream()
                .filter(naissance -> !naissance.isAfter(reference))
                .filter(naissance -> Period.between(naissance, reference).getYears() < bareme.ageMaxEnfant())
                .count();
    }

    /** Majoration pour enfants à charge, plafond compris. */
    public int supplementEnfants(int annee, DroitsEmployeSnapshot employe, BaremeConges bareme) {
        int jours = enfantsBeneficiaires(annee, employe, bareme) * bareme.joursParEnfant();
        Integer plafond = bareme.plafondJoursEnfants();
        return plafond == null ? jours : Math.min(jours, plafond);
    }

    /** Majoration du palier d'ancienneté atteint — non cumulative, cf. {@link BaremeConges#joursPourAnciennete}. */
    public int supplementAnciennete(int annee, LocalDate dateEntree, BaremeConges bareme) {
        return bareme.joursPourAnciennete(anneesAnciennete(annee, dateEntree));
    }

    /**
     * Ventilation complète des droits de l'exercice.
     *
     * <p>⚠ <b>Garde commune aux deux majorations</b> : sans aucun mois de service effectif sur
     * l'exercice, elles valent 0. Sans elle, un exercice antérieur à l'embauche — ou un employé
     * pas encore entré — se verrait créditer des jours d'ancienneté ou d'enfants alors qu'il
     * n'a aucun droit de base, et ce crédit remonterait dans le reliquat reporté.
     */
    public DetailAcquis acquis(int annee, DroitsEmployeSnapshot employe,
                               LocalDate aujourdhui, BaremeConges bareme) {
        int mois = moisAcquis(annee, employe.dateEntree(), aujourdhui);
        int base = mois * bareme.joursAcquisParMois();

        if (mois == 0) {
            return new DetailAcquis(0, base, 0, 0, 0, 0);
        }

        int beneficiaires = enfantsBeneficiaires(annee, employe, bareme);
        int annees = anneesAnciennete(annee, employe.dateEntree());

        int supplementEnfants = proratiser(supplementEnfants(annee, employe, bareme), mois, bareme);
        int supplementAnciennete = proratiser(bareme.joursPourAnciennete(annees), mois, bareme);

        return new DetailAcquis(mois, base, supplementEnfants, supplementAnciennete,
                annees, beneficiaires);
    }

    /** 31 décembre de l'exercice — la date à laquelle s'apprécient âge et ancienneté. */
    private static LocalDate referenceExercice(int annee) {
        return LocalDate.of(annee, 12, 31);
    }

    private static int proratiser(int jours, int moisAcquis, BaremeConges bareme) {
        return bareme.proratiserSupplements() ? jours * Math.min(moisAcquis, 12) / 12 : jours;
    }
}
