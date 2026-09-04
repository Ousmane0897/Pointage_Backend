package com.example.Pointage_Cleanic.util;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sémantique d'une affectation : close, active, identifiée.
 * <p>
 * Date de référence <b>figée</b> : une horloge système rendrait ces résultats
 * dépendants du jour d'exécution.
 */
class AffectationSiteUtilsTest {

    private static final LocalDate AUJOURD_HUI = LocalDate.of(2026, 9, 4);

    private AffectationSite avecSortie(String site, LocalDate sortie) {
        return AffectationSite.builder().site(site)
                .dateEntree(LocalDate.of(2026, 1, 1)).dateSortie(sortie).build();
    }

    @Test
    void une_sortie_passee_cloture_l_affectation() {
        assertThat(AffectationSiteUtils.estTerminee(
                avecSortie("Yoff", LocalDate.of(2026, 9, 3)), AUJOURD_HUI)).isTrue();
    }

    @Test
    void une_sortie_du_jour_meme_ne_cloture_pas() {
        // L'agent travaille encore aujourd'hui : l'exclure ferait disparaître ses
        // créneaux du jour de tous les écrans qui s'appuient sur cette règle.
        assertThat(AffectationSiteUtils.estTerminee(
                avecSortie("Yoff", AUJOURD_HUI), AUJOURD_HUI)).isFalse();
    }

    @Test
    void une_sortie_future_ou_absente_ne_cloture_pas() {
        assertThat(AffectationSiteUtils.estTerminee(
                avecSortie("Yoff", LocalDate.of(2026, 12, 31)), AUJOURD_HUI)).isFalse();
        assertThat(AffectationSiteUtils.estTerminee(
                avecSortie("Yoff", null), AUJOURD_HUI)).isFalse();
    }

    @Test
    void actives_et_terminees_partitionnent_la_liste() {
        AffectationSite close = avecSortie("Ouakam", LocalDate.of(2026, 6, 30));
        AffectationSite enCours = avecSortie("Yoff", null);

        assertThat(AffectationSiteUtils.actives(List.of(close, enCours), AUJOURD_HUI))
                .extracting(AffectationSite::getSite).containsExactly("Yoff");
        assertThat(AffectationSiteUtils.terminees(List.of(close, enCours), AUJOURD_HUI))
                .extracting(AffectationSite::getSite).containsExactly("Ouakam");
    }

    @Test
    void actives_tolere_une_liste_nulle_ou_des_elements_nuls() {
        assertThat(AffectationSiteUtils.actives(null, AUJOURD_HUI)).isEmpty();

        List<AffectationSite> avecNull = new ArrayList<>();
        avecNull.add(null);
        avecNull.add(avecSortie("Yoff", null));
        assertThat(AffectationSiteUtils.actives(avecNull, AUJOURD_HUI)).hasSize(1);
    }

    @Test
    void assurerIds_ne_pose_un_id_que_sur_les_lignes_qui_en_manquent() {
        AffectationSite deja = AffectationSite.builder().site("Yoff").id("aff-1").build();
        AffectationSite sans = AffectationSite.builder().site("Ouakam").build();

        assertThat(AffectationSiteUtils.assurerIds(List.of(deja, sans))).isTrue();
        assertThat(deja.getId()).isEqualTo("aff-1");
        assertThat(sans.getId()).isNotNull();
    }

    @Test
    void assurerIds_est_idempotent() {
        List<AffectationSite> affectations = List.of(
                AffectationSite.builder().site("Yoff").build());
        AffectationSiteUtils.assurerIds(affectations);
        String pose = affectations.get(0).getId();

        // Second passage : rien à faire, donc rien à réécrire en base.
        assertThat(AffectationSiteUtils.assurerIds(affectations)).isFalse();
        assertThat(affectations.get(0).getId()).isEqualTo(pose);
    }

    @Test
    void assurerIds_traite_un_id_blanc_comme_absent() {
        AffectationSite blanc = AffectationSite.builder().site("Yoff").id("  ").build();

        assertThat(AffectationSiteUtils.assurerIds(List.of(blanc))).isTrue();
        assertThat(blanc.getId()).isNotBlank().isNotEqualTo("  ");
    }

    @Test
    void la_signature_ignore_la_casse_et_les_espaces_du_nom_de_site() {
        AffectationSite a = avecSortie(" Yoff ", LocalDate.of(2026, 6, 30));
        AffectationSite b = avecSortie("yoff", LocalDate.of(2026, 6, 30));

        assertThat(AffectationSiteUtils.signature(a))
                .isEqualTo(AffectationSiteUtils.signature(b));
    }

    @Test
    void la_signature_distingue_deux_passages_sur_le_meme_site() {
        AffectationSite premier = AffectationSite.builder().site("Yoff")
                .dateEntree(LocalDate.of(2025, 1, 1)).dateSortie(LocalDate.of(2025, 6, 30)).build();
        AffectationSite retour = AffectationSite.builder().site("Yoff")
                .dateEntree(LocalDate.of(2026, 9, 1)).build();

        assertThat(AffectationSiteUtils.signature(premier))
                .isNotEqualTo(AffectationSiteUtils.signature(retour));
    }

    @Test
    void la_signature_ne_depend_ni_des_horaires_ni_du_rythme() {
        // Ces champs sont volontairement hors de la clé : les y inclure rendrait le
        // rapprochement sensible au moindre écart d'aller-retour et bloquerait
        // l'enregistrement du dossier entier.
        AffectationSite sans = AffectationSite.builder().site("Yoff")
                .dateEntree(LocalDate.of(2026, 1, 1)).build();
        AffectationSite avec = AffectationSite.builder().site("Yoff")
                .dateEntree(LocalDate.of(2026, 1, 1))
                .horaireDebut("06:00").horaireFin("12:00").joursTravail("LUN_SAM").build();

        assertThat(AffectationSiteUtils.signature(sans))
                .isEqualTo(AffectationSiteUtils.signature(avec));
    }
}
