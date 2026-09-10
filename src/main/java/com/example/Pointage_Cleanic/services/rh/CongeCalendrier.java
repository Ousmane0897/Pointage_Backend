package com.example.Pointage_Cleanic.services.rh;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Décompte des jours de congé.
 *
 * <p>Les congés sont exprimés en <b>jours ouvrés</b> : samedi, dimanche et jours fériés ne
 * sont pas décomptés. C'est la même unité que le solde annuel acquis — auparavant le
 * décompte portait sur des jours calendaires alors que le solde était en jours ouvrés, et
 * le solde se vidait donc trop vite.
 *
 * <p>⚠ <b>Les jours fériés sont PASSÉS en argument</b>, jamais lus ici : la classe reste
 * <b>pure et statique</b>, testable sans base ni horloge. L'appelant les charge une fois par
 * méthode publique via {@link JourFerieService#datesFeriees} — même discipline que
 * {@code PerimetreConges} et {@code BaremeConges}. Un ensemble {@code null} ou vide
 * reproduit exactement le comportement antérieur au référentiel.
 *
 * <p>⚠ <b>Le décompte reste lundi-vendredi</b> et ne suit PAS le rythme de l'agent.
 * L'acquis est de 2 jours <i>ouvrables</i> par mois (droit sénégalais) et les deux unités
 * doivent concorder : basculer le décompte sur {@code LUN_SAM} sans basculer l'acquis
 * viderait les soldes des agents de terrain 20 % plus vite. À trancher dans un lot dédié si
 * le besoin est confirmé.
 */
public final class CongeCalendrier {

    private CongeCalendrier() {
    }

    /**
     * Nombre de jours ouvrés entre deux dates incluses, jours fériés exclus.
     * Renvoie 0 si une date manque, si l'intervalle est inversé, ou si la période ne
     * couvre qu'un week-end.
     */
    public static int joursOuvres(LocalDate debut, LocalDate fin, Set<LocalDate> feries) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            return 0;
        }
        int jours = 0;
        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            if (estOuvre(d, feries)) {
                jours++;
            }
        }
        return jours;
    }

    /**
     * Variante <b>sans</b> calendrier de fériés.
     *
     * <p>⚠ Réservée aux appelants qui n'ont légitimement pas à consulter le référentiel —
     * la migration des demandes antérieures, notamment, qui doit reproduire le décompte tel
     * qu'il était au moment de la saisie. Tout <b>nouveau</b> calcul doit passer la
     * surcharge à trois arguments, faute de quoi les fériés redeviendraient décomptés en
     * silence.
     */
    public static int joursOuvres(LocalDate debut, LocalDate fin) {
        return joursOuvres(debut, fin, Set.of());
    }

    /** Jour travaillé : ni week-end, ni férié. */
    public static boolean estOuvre(LocalDate date, Set<LocalDate> feries) {
        if (date == null) return false;
        if (feries != null && feries.contains(date)) return false;
        DayOfWeek jour = date.getDayOfWeek();
        return jour != DayOfWeek.SATURDAY && jour != DayOfWeek.SUNDAY;
    }

    public static boolean estOuvre(LocalDate date) {
        return estOuvre(date, Set.of());
    }
}
