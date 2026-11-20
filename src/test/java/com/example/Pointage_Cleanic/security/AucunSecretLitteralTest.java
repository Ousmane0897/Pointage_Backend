package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C3 — garde-fou : aucune propriété sensible ne porte de valeur littérale dans les fichiers
 * suivis par git ({@code application*.yml} hors profil de test, {@code docker-compose.yml}).
 * Chaque valeur doit être une référence {@code ${VARIABLE}}.
 *
 * <p>Le message d'échec donne le fichier et la clé, jamais la valeur.
 */
class AucunSecretLitteralTest {

    /** Clés YAML sensibles : secret, password, key, api-key, token… */
    private static final Pattern CLE_SENSIBLE = Pattern.compile(
            "^\\s*([A-Za-z0-9_.-]*(secret|password|passwd|key|token|api-key|apikey))\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    @Test
    void application_yml_sans_secret_litteral() throws IOException {
        List<String> fautes = new ArrayList<>();
        try (Stream<Path> fichiers = Files.list(Path.of("src/main/resources"))) {
            fichiers.filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml"))
                    .filter(p -> !p.getFileName().toString().contains("-test"))
                    .forEach(p -> fautes.addAll(analyser(p)));
        }
        assertThat(fautes).as("propriétés sensibles en clair").isEmpty();
    }

    @Test
    void docker_compose_sans_secret_litteral() throws IOException {
        assertThat(analyser(Path.of("docker-compose.yml"))).as("docker-compose.yml").isEmpty();
    }

    private static List<String> analyser(Path fichier) {
        List<String> fautes = new ArrayList<>();
        List<String> lignes;
        try {
            lignes = Files.readAllLines(fichier);
        } catch (IOException e) {
            throw new IllegalStateException(fichier.toString(), e);
        }
        for (int i = 0; i < lignes.size(); i++) {
            String ligne = lignes.get(i);
            if (ligne.trim().startsWith("#")) {
                continue;
            }
            Matcher m = CLE_SENSIBLE.matcher(ligne);
            if (!m.matches()) {
                continue;
            }
            String valeur = m.group(3).trim().replaceAll("^[\"']|[\"']$", "");
            if (valeur.isEmpty() || valeur.startsWith("${")) {
                continue; // bloc YAML parent ou référence à une variable
            }
            fautes.add(fichier + ":" + (i + 1) + " (" + m.group(1) + ")");
        }
        return fautes;
    }
}
