package com.example.Pointage_Cleanic.migrations.index;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le script d'exploitation {@code ops/mongo/detecter-doublons.js} (lecture seule, joué en
 * production avant la mise en service) liste les mêmes index uniques qu'{@link IndexesAttendus},
 * ni plus ni moins : sinon il annoncerait « OK » sur un index qui bloquera le démarrage.
 */
class DetecterDoublonsScriptTest {

    private static final Pattern ENTREE = Pattern.compile("^\\s*\\['([^']+)', '([^']+)',", Pattern.MULTILINE);

    @Test
    void le_script_couvre_exactement_les_index_uniques() throws IOException {
        String script = Files.readString(Path.of("ops/mongo/detecter-doublons.js"));
        List<String> dansLeScript = ENTREE.matcher(script).results()
                .map(m -> m.group(1) + "." + m.group(2)).sorted().toList();
        List<String> uniques = IndexesAttendus.tous().stream().filter(IndexAttendu::unique)
                .map(i -> i.collection() + "." + i.nom()).sorted().toList();

        assertThat(dansLeScript).isEqualTo(uniques);
    }

    @Test
    void le_script_reprend_le_filtre_partiel_de_chaque_index() throws IOException {
        String script = Files.readString(Path.of("ops/mongo/detecter-doublons.js"));
        for (IndexAttendu index : IndexesAttendus.tous()) {
            if (!index.unique()) {
                continue;
            }
            Matcher ligne = Pattern.compile("\\['" + Pattern.quote(index.collection()) + "', '"
                    + Pattern.quote(index.nom()) + "', [^\\n]*").matcher(script);
            assertThat(ligne.find()).as(index.nom()).isTrue();
            boolean sansFiltre = ligne.group().trim().endsWith("null],");
            assertThat(sansFiltre).as("filtre de " + index.collection() + "." + index.nom())
                    .isEqualTo(index.filtrePartiel() == null);
        }
    }
}
