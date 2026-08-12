package com.example.Pointage_Cleanic.Enum.rh;

/**
 * Rythme de travail hebdomadaire d'un dossier employé (RH — Gestion du personnel).
 * <ul>
 *   <li>{@code LUN_VEN} — Lundi à Vendredi</li>
 *   <li>{@code LUN_SAM} — Lundi à Samedi</li>
 *   <li>{@code LUN_DIM} — Lundi à Dimanche</li>
 * </ul>
 * Sert d'oracle de validation : le champ {@code joursTravail} est porté en {@code String}
 * (nullable, rétro-compat) et validé contre cet ensemble côté service.
 */
public enum JoursTravail {
    LUN_VEN, LUN_SAM, LUN_DIM
}
