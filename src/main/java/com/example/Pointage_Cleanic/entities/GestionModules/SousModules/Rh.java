package com.example.Pointage_Cleanic.entities.GestionModules.SousModules;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sous-flags du module RH (6.1 → 6.4), portés dans le claim JWT {@code modules.rh}.
 * Auparavant {@code modulesAutorises.rh} était un simple booléen ; il est désormais un
 * objet imbriqué de 17 sous-flags par fonctionnalité, exactement comme {@code stock} /
 * {@code terrain} / {@code productionChimie}. Le gating fin est délégué au frontend Angular
 * (cf. CLAUDE.md) — aucune logique métier ne dépend de ces valeurs côté serveur.
 *
 * <p>Les clés camelCase ci-dessous sont le contrat figé lu par le front : ne rien renommer.
 *
 * <p>{@link RhDeserializer} rend la désérialisation Jackson tolérante à un ancien
 * {@code rh} booléen ({@code true} ⇒ tous les flags vrais, {@code false} ⇒ tous faux).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize(using = RhDeserializer.class)
public class Rh {

    // --- 6.1 Gestion du Personnel ---
    private boolean dossierEmploye;
    private boolean contrats;
    private boolean organigramme;
    private boolean documents;

    // --- 6.2 Temps & Présences ---
    private boolean pointageCentralise;
    private boolean absences;
    private boolean conges;
    private boolean heuresSupplementaires;
    private boolean recapitulatif;

    // --- 6.3 Paie ---
    private boolean grilleSalariale;
    private boolean calculBulletin;
    private boolean historiquePaies;
    private boolean declarations;

    // --- 6.4 Développement RH ---
    private boolean formations;
    private boolean evaluations;
    private boolean sanctions;
    private boolean tableauBordRh;
}