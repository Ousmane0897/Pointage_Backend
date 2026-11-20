package com.example.Pointage_Cleanic.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.List;

/**
 * Refuse de démarrer en profil {@code prod} si un secret obligatoire manque.
 *
 * <p>Enregistré dans {@code META-INF/spring.factories} : un {@link EnvironmentPostProcessor} est
 * instancié avant le contexte, donc {@code @Profile} n'y aurait aucun effet — le profil actif est
 * lu ici même. Exécuté en dernier ({@link Ordered#LOWEST_PRECEDENCE}) pour voir l'environnement
 * complet (variables d'environnement, {@code .env} importé, fichiers {@code application-*.yml}).
 *
 * <p>Le message d'erreur ne liste que les <b>noms</b> des variables manquantes, jamais de valeur.
 */
public class SecretsRequisVerificateur implements EnvironmentPostProcessor, Ordered {

    /** Variables sans lesquelles la production ne doit pas démarrer. */
    static final List<String> SECRETS_REQUIS_PROD = List.of(
            "JWT_SECRET",
            "MAIL_USERNAME",
            "MAIL_PASSWORD",
            "GOOGLE_MAPS_API_KEY"
    );

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        verifier(environment);
    }

    static void verifier(ConfigurableEnvironment environment) {
        boolean prod = Arrays.stream(environment.getActiveProfiles()).anyMatch("prod"::equalsIgnoreCase);
        if (!prod) {
            return;
        }
        List<String> manquants = SECRETS_REQUIS_PROD.stream()
                .filter(nom -> {
                    String valeur = environment.getProperty(nom);
                    return valeur == null || valeur.isBlank();
                })
                .toList();
        if (!manquants.isEmpty()) {
            throw new IllegalStateException(
                    "Démarrage refusé (profil prod) : variable(s) d'environnement obligatoire(s) absente(s) ou vide(s) : "
                            + String.join(", ", manquants)
                            + ". Renseignez-les dans le fichier .env du serveur (modèle : .env.example).");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
