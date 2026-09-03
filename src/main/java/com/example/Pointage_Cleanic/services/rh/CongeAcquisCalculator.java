package com.example.Pointage_Cleanic.services.rh;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Droits à congés payés acquis, au sens du droit du travail sénégalais :
 * <b>2 jours ouvrables par mois de service effectif</b> (24 jours pour une année pleine).
 *
 * <p>Remplace la constante annuelle unique qui prévalait auparavant : celle-ci accordait le même
 * nombre de jours à un employé embauché le 15 novembre qu'à un employé présent toute l'année.
 *
 * <p>Ce composant est volontairement <b>pur</b> — aucune dépendance hors la valeur de
 * configuration, aucun accès base, l'« aujourd'hui » est toujours passé en paramètre. C'est ce
 * qui permet de le tester sans contexte Spring et sans horloge figée.
 */
@Component
public class CongeAcquisCalculator {

    /**
     * Jours ouvrables acquis par mois de service effectif.
     * 2 est la valeur légale sénégalaise ; configurable pour un régime plus favorable.
     */
    @Value("${app.conges.jours-acquis-par-mois:2}")
    private int joursAcquisParMois;

    public int getJoursAcquisParMois() {
        return joursAcquisParMois;
    }

    /**
     * Mois de service effectif <b>révolus</b> de l'employé sur l'exercice {@code annee}.
     *
     * <p>Le décompte est de quantième à quantième : une entrée le 15/03 vaut 5 mois au 02/09
     * (15/03 → 15/08), pas 6. C'est bien la notion de « service effectif ».
     *
     * <p>Sur un exercice clos, la borne est le 1er janvier suivant et non le 31 décembre :
     * {@code MONTHS.between(2025-01-01, 2025-12-31)} vaut 11, ce qui amputerait d'un mois toute
     * année pleine.
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

    /** Jours acquis sur l'exercice — cf. {@link #moisAcquis}. */
    public int acquis(int annee, LocalDate dateEntree, LocalDate aujourdhui) {
        return moisAcquis(annee, dateEntree, aujourdhui) * joursAcquisParMois;
    }
}
