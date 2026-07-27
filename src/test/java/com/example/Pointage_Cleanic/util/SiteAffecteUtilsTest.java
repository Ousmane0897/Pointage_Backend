package com.example.Pointage_Cleanic.util;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SiteAffecteUtilsTest {

    @Test
    void decouper_sur_slash() {
        assertThat(SiteAffecteUtils.decouper("A / B / C"))
                .containsExactly("A", "B", "C");
    }

    @Test
    void decouper_sur_tiret_entoure_despaces() {
        assertThat(SiteAffecteUtils.decouper("Sacré-Coeur - Point E"))
                .containsExactly("Sacré-Coeur", "Point E");
    }

    @Test
    void decouper_sur_virgule() {
        assertThat(SiteAffecteUtils.decouper("A, B"))
                .containsExactly("A", "B");
    }

    @Test
    void decouper_preserve_le_tiret_colle() {
        // "Sacré-Coeur" ne doit PAS être découpé (tiret sans espaces autour).
        assertThat(SiteAffecteUtils.decouper("Sacré-Coeur"))
                .containsExactly("Sacré-Coeur");
    }

    @Test
    void decouper_null_ou_blank_donne_liste_vide() {
        assertThat(SiteAffecteUtils.decouper(null)).isEmpty();
        assertThat(SiteAffecteUtils.decouper("   ")).isEmpty();
    }

    @Test
    void decouper_ignore_les_segments_vides_et_trim() {
        assertThat(SiteAffecteUtils.decouper("  A  /  / B "))
                .containsExactly("A", "B");
    }

    @Test
    void affectationsDepuisSiteAffecte_sites_sans_horaires() {
        List<AffectationSite> affectations =
                SiteAffecteUtils.affectationsDepuisSiteAffecte("Sacré-Coeur - Point E");

        assertThat(affectations).hasSize(2);
        assertThat(affectations.get(0).getSite()).isEqualTo("Sacré-Coeur");
        assertThat(affectations.get(0).getHoraireDebut()).isNull();
        assertThat(affectations.get(0).getHoraireFin()).isNull();
        assertThat(affectations.get(1).getSite()).isEqualTo("Point E");
    }
}
