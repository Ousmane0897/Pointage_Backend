package com.example.Pointage_Cleanic.services.rh;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Décompte des jours de congé.
 *
 * <p>Les congés sont exprimés en <b>jours ouvrés</b> : samedi et dimanche ne sont pas
 * décomptés. C'est la même unité que le solde annuel acquis — auparavant le décompte
 * portait sur des jours calendaires alors que le solde était en jours ouvrés, et le solde
 * se vidait donc trop vite.
 *
 * <p><b>Limite connue :</b> les jours fériés <i>sont</i> décomptés. Le référentiel des
 * jours fériés a été supprimé de l'application et il n'existe aucune source fiable à
 * interroger ; une liste en dur serait pire qu'une limite documentée.
 */
public final class CongeCalendrier {

    private CongeCalendrier() {
    }

    /**
     * Nombre de jours ouvrés entre deux dates incluses.
     * Renvoie 0 si une date manque, si l'intervalle est inversé, ou si la période ne
     * couvre qu'un week-end.
     */
    public static int joursOuvres(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            return 0;
        }
        int jours = 0;
        for (LocalDate d = debut; !d.isAfter(fin); d = d.plusDays(1)) {
            if (estOuvre(d)) {
                jours++;
            }
        }
        return jours;
    }

    public static boolean estOuvre(LocalDate date) {
        DayOfWeek jour = date.getDayOfWeek();
        return jour != DayOfWeek.SATURDAY && jour != DayOfWeek.SUNDAY;
    }
}
