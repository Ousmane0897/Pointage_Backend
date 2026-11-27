package com.example.Pointage_Cleanic.migrations;

/**
 * Garde-fou commun aux jeux de données des essais de bout en bout (profil {@code e2e}) : ils ne
 * s'appliquent qu'à une base dont le nom contient {@code e2e}, jamais à la base de développement
 * {@code cleanic} ni à la production, même si le profil était activé par erreur.
 */
public final class E2eGardeFou {

    private E2eGardeFou() {
    }

    public static void verifierBase(String nomBase) {
        if (nomBase == null || !nomBase.toLowerCase().contains("e2e")) {
            throw new IllegalStateException("Profil e2e refusé : la base « " + nomBase
                    + " » n'est pas une base d'essai (son nom doit contenir « e2e »).");
        }
    }

    public static String exiger(String nomVariable, String valeur) {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalStateException("Profil e2e : la variable d'environnement "
                    + nomVariable + " est obligatoire (voir docs/tests/e2e.md du front).");
        }
        return valeur;
    }
}
