package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Enum.CategorieCritere;
import com.example.Pointage_Cleanic.Enum.NotationAlphabetique;
import com.example.Pointage_Cleanic.entities.CritereEvaluation;
import com.example.Pointage_Cleanic.entities.NoteEvaluation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests unitaires du moteur de calcul de la note globale et du mapping
 * alphabétique. Aucune dépendance Spring ni Mongo.
 */
class EvaluationPeriodiqueServiceTest {

    private final EvaluationPeriodiqueService service =
            new EvaluationPeriodiqueService(null, null, null, null);

    @Test
    void calculer_note_globale_moyenne_ponderee() {
        List<CritereEvaluation> criteres = List.of(
                critere("TECH", 30),
                critere("AUTO", 20),
                critere("COMM", 15),
                critere("EQUIP", 15),
                critere("OBJ", 20)
        );
        List<NoteEvaluation> notes = List.of(
                note("TECH", 4),
                note("AUTO", 5),
                note("COMM", 3),
                note("EQUIP", 4),
                note("OBJ", 5)
        );

        // 4×30 + 5×20 + 3×15 + 4×15 + 5×20 = 120+100+45+60+100 = 425
        // 425 / 100 = 4.25
        double note = service.calculerNoteGlobale(notes, criteres);
        assertThat(note).isCloseTo(4.25, within(0.01));
    }

    @Test
    void calculer_note_globale_ignore_criteres_non_notes() {
        List<CritereEvaluation> criteres = List.of(
                critere("A", 50),
                critere("B", 50)
        );
        List<NoteEvaluation> notes = List.of(note("A", 4));

        // Seul A noté → poids total considéré = 50, note = 4.0
        assertThat(service.calculerNoteGlobale(notes, criteres)).isEqualTo(4.0);
    }

    @Test
    void calculer_note_globale_liste_vide() {
        assertThat(service.calculerNoteGlobale(List.of(), List.of())).isZero();
    }

    @Test
    void mapping_alphabetique_seuils() {
        assertThat(service.mapAlphabetique(5.0)).isEqualTo(NotationAlphabetique.A);
        assertThat(service.mapAlphabetique(4.5)).isEqualTo(NotationAlphabetique.A);
        assertThat(service.mapAlphabetique(4.49)).isEqualTo(NotationAlphabetique.B);
        assertThat(service.mapAlphabetique(3.5)).isEqualTo(NotationAlphabetique.B);
        assertThat(service.mapAlphabetique(3.49)).isEqualTo(NotationAlphabetique.C);
        assertThat(service.mapAlphabetique(2.5)).isEqualTo(NotationAlphabetique.C);
        assertThat(service.mapAlphabetique(2.49)).isEqualTo(NotationAlphabetique.D);
        assertThat(service.mapAlphabetique(0.0)).isEqualTo(NotationAlphabetique.D);
    }

    private CritereEvaluation critere(String code, int poids) {
        return CritereEvaluation.builder()
                .code(code)
                .libelle(code)
                .poids(poids)
                .categorie(CategorieCritere.TECHNIQUE)
                .build();
    }

    private NoteEvaluation note(String code, int valeur) {
        return NoteEvaluation.builder()
                .critereCode(code)
                .note(valeur)
                .build();
    }
}
