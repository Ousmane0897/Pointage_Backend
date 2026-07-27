package com.example.Pointage_Cleanic.Enum.terrain;

/**
 * Périmètre de comptage de l'effectif d'un site client.
 *
 * <p>Les deux valeurs mesurent volontairement deux populations différentes qui
 * partagent le même plafond {@code nombreMaxEmployes} :</p>
 * <ul>
 *   <li>{@link #RH} : employés (dossiers) rattachés au site par NOM (affectations
 *       du dossier employé, fallback sur la chaîne {@code siteAffecte}) ;</li>
 *   <li>{@link #TERRAIN} : affectations de planning terrain sur le {@code siteId}
 *       dont le statut n'est pas {@code ANNULEE}.</li>
 * </ul>
 */
public enum PerimetreEffectif {
    RH,
    TERRAIN
}
