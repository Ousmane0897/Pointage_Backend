package com.example.Pointage_Cleanic.util;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * « Cet employé est-il rattaché à ce site aujourd'hui ? » — règle partagée par le
 * décompte d'effectif et le contrôle « pointage hors affectation ». Date figée.
 */
class RattachementSiteUtilsTest {

    private static final LocalDate AUJOURD_HUI = LocalDate.of(2026, 10, 12);

    private static DossierEmploye dossier(String siteAffecte, AffectationSite... affectations) {
        DossierEmploye d = new DossierEmploye();
        d.setSiteAffecte(siteAffecte);
        d.setAffectations(affectations.length == 0 ? null : List.of(affectations));
        return d;
    }

    private static AffectationSite affectation(String site, LocalDate sortie) {
        return AffectationSite.builder().site(site)
                .dateEntree(LocalDate.of(2026, 1, 1)).dateSortie(sortie).build();
    }

    @Test
    void comparaison_exacte_insensible_a_la_casse_et_aux_espaces() {
        DossierEmploye d = dossier(null, affectation("SGS Mermoz", null));

        assertThat(RattachementSiteUtils.estAffecteAuSite(d, "  sgs mermoz ", AUJOURD_HUI)).isTrue();
        // Pas de sous-chaîne : « SGS » n'est pas « SGS Mermoz ».
        assertThat(RattachementSiteUtils.estAffecteAuSite(d, "SGS", AUJOURD_HUI)).isFalse();
    }

    @Test
    void une_affectation_close_ne_rattache_plus() {
        DossierEmploye d = dossier("SGS Mermoz", affectation("SGS Mermoz", LocalDate.of(2026, 10, 11)));

        assertThat(RattachementSiteUtils.estAffecteAuSite(d, "SGS Mermoz", AUJOURD_HUI)).isFalse();
    }

    @Test
    void la_sortie_du_jour_meme_rattache_encore() {
        DossierEmploye d = dossier(null, affectation("SGS Mermoz", AUJOURD_HUI));

        assertThat(RattachementSiteUtils.estAffecteAuSite(d, "SGS Mermoz", AUJOURD_HUI)).isTrue();
    }

    @Test
    void les_affectations_structurees_priment_sur_site_affecte() {
        // siteAffecte mentionne encore Yoff, mais l'affectation structurée est close :
        // on ne retombe PAS sur la chaîne legacy.
        DossierEmploye d = dossier("Yoff", affectation("Yoff", LocalDate.of(2026, 1, 31)));

        assertThat(RattachementSiteUtils.estAffecteAuSite(d, "Yoff", AUJOURD_HUI)).isFalse();
    }

    @Test
    void repli_sur_site_affecte_pour_un_dossier_sans_affectations() {
        DossierEmploye d = dossier("Keur Gorgui / BGFI Tann");

        assertThat(RattachementSiteUtils.estAffecteAuSite(d, "bgfi tann", AUJOURD_HUI)).isTrue();
        assertThat(RattachementSiteUtils.estAffecteAuSite(d, "Yoff", AUJOURD_HUI)).isFalse();
    }

    @Test
    void entrees_vides_ne_rattachent_jamais() {
        assertThat(RattachementSiteUtils.estAffecteAuSite(null, "Yoff", AUJOURD_HUI)).isFalse();
        assertThat(RattachementSiteUtils.estAffecteAuSite(dossier("Yoff"), " ", AUJOURD_HUI)).isFalse();
        assertThat(RattachementSiteUtils.estAffecteAuSite(dossier(null), "Yoff", AUJOURD_HUI)).isFalse();
    }
}
