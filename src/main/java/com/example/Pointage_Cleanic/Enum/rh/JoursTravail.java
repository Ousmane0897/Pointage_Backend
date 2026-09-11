package com.example.Pointage_Cleanic.Enum.rh;

/**
 * Rythme de travail hebdomadaire d'un dossier employé (RH — Gestion du personnel).
 * <ul>
 *   <li>{@code LUN_VEN} — Lundi à Vendredi</li>
 *   <li>{@code LUN_SAM} — Lundi à Samedi</li>
 *   <li>{@code LUN_DIM} — Lundi à Dimanche</li>
 *   <li>{@code PERSONNALISE} — jours donnés en clair par
 *       {@code AffectationSite.joursSemaine}</li>
 * </ul>
 * Sert d'oracle de validation : le champ {@code joursTravail} est porté en {@code String}
 * (nullable, rétro-compat) et validé contre cet ensemble côté service.
 *
 * <p>⚠ <b>{@code PERSONNALISE} n'est pas un rythme, c'est un marqueur</b> : il signale que la
 * semaine ouvrée est portée par
 * {@link com.example.Pointage_Cleanic.entities.rh.AffectationSite#getJoursSemaine()}. C'est le
 * seul moyen d'exprimer « lundi, mercredi, vendredi » — un rythme que les trois préréglages ne
 * savent pas décrire, et qui faisait compter l'agent ABSENT les quatre autres jours.
 *
 * <p>⚠ <b>Valide au niveau d'une affectation site uniquement.</b> Sur
 * {@code DossierEmploye.joursTravail} il ne désignerait aucune liste, et le backfill le
 * recopierait sur des affectations dépourvues de jours — donc en échelon permissif silencieux.
 * {@code DossierEmployeService.validerJoursTravail} le refuse à ce niveau.
 */
public enum JoursTravail {
    LUN_VEN, LUN_SAM, LUN_DIM, PERSONNALISE
}
