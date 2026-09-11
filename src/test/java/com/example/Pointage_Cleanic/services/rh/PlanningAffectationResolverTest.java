package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Semaine ouvrée et jour de repos hebdomadaire.
 *
 * <p>Aucun test ne couvrait ce résolveur : l'échelle de replis « site → employé → aucun
 * filtrage » n'était garantie que par sa Javadoc, alors qu'un repli sur {@code LUN_VEN}
 * masquerait les absences du samedi et du dimanche de tout dossier legacy.
 */
class PlanningAffectationResolverTest {

    private final PlanningAffectationResolver resolver = new PlanningAffectationResolver();

    private static AffectationSite site(String joursTravail, Integer jourRepos) {
        return site(joursTravail, jourRepos, null);
    }

    private static AffectationSite site(String joursTravail, Integer jourRepos,
                                        List<Integer> joursSemaine) {
        return AffectationSite.builder()
                .site("Praline")
                .joursTravail(joursTravail)
                .jourRepos(jourRepos)
                .joursSemaine(joursSemaine)
                .build();
    }

    private static DossierEmploye employe(String joursTravail) {
        DossierEmploye e = new DossierEmploye();
        e.setJoursTravail(joursTravail);
        return e;
    }

    // --- Semaine ouvrée seule (comportement antérieur, à ne pas altérer) -----

    @Test
    void lun_ven_exclut_le_week_end() {
        AffectationSite a = site("LUN_VEN", null);
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.FRIDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SATURDAY)).isFalse();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    void lun_sam_travaille_le_samedi_et_se_repose_le_dimanche() {
        AffectationSite a = site("LUN_SAM", null);
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SATURDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    void rythme_absent_sur_le_site_retombe_sur_celui_de_l_employe() {
        AffectationSite a = site(null, null);
        assertThat(resolver.jourOuvre(a, employe("LUN_VEN"), DayOfWeek.SATURDAY)).isFalse();
    }

    @Test
    void aucun_rythme_connu_ne_filtre_rien() {
        // ⚠ Repli délibérément permissif : replier sur LUN_VEN masquerait une absence
        // réelle du samedi ou du dimanche, le pire mode de défaillance de cet écran.
        AffectationSite a = site(null, null);
        assertThat(resolver.jourOuvre(a, employe(null), DayOfWeek.SUNDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isTrue();
    }

    @Test
    void un_rythme_corrompu_ne_filtre_rien() {
        assertThat(resolver.jourOuvre(site("N_IMPORTE_QUOI", null), null, DayOfWeek.SUNDAY)).isTrue();
    }

    // --- Jour de repos hebdomadaire -----------------------------------------

    @Test
    void sans_jour_de_repos_le_parc_existant_est_inchange() {
        AffectationSite a = site("LUN_SAM", null);
        for (DayOfWeek j : DayOfWeek.values()) {
            assertThat(resolver.jourOuvre(a, null, j))
                    .as("%s", j)
                    .isEqualTo(j != DayOfWeek.SUNDAY);
        }
    }

    @Test
    void le_jour_de_repos_saisi_est_retire_le_cas_praline() {
        // Restaurant ouvert le dimanche : l'agent y travaille et se repose le mardi.
        AffectationSite a = site("LUN_DIM", 2);
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.TUESDAY)).isFalse();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.MONDAY)).isTrue();
    }

    @Test
    void un_repos_le_dimanche_se_note_0_comme_cote_front() {
        assertThat(resolver.jourOuvre(site("LUN_DIM", 0), null, DayOfWeek.SUNDAY)).isFalse();
        assertThat(resolver.jourOuvre(site("LUN_DIM", 0), null, DayOfWeek.SATURDAY)).isTrue();
    }

    @Test
    void le_7_iso_est_tolere_pour_dimanche() {
        // Une écriture directe en base au format ISO ne doit pas passer inaperçue.
        assertThat(resolver.jourOuvre(site("LUN_DIM", 7), null, DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    void un_jour_hors_semaine_ouvree_reste_ferme() {
        AffectationSite a = site("LUN_SAM", 2);
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isFalse();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.TUESDAY)).isFalse();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SATURDAY)).isTrue();
    }

    @Test
    void le_jour_de_repos_est_sans_effet_sur_lun_ven() {
        // Rythme changé après coup : sans cette garde, la semaine tomberait à 4 jours.
        AffectationSite a = site("LUN_VEN", 2);
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.TUESDAY)).isTrue();
    }

    @Test
    void le_jour_de_repos_s_applique_meme_sans_rythme_connu() {
        // Un jour de repos explicite a été saisi par la RH : c'est une information sûre,
        // contrairement à un rythme absent.
        AffectationSite a = site(null, 2);
        assertThat(resolver.jourOuvre(a, employe(null), DayOfWeek.TUESDAY)).isFalse();
        assertThat(resolver.jourOuvre(a, employe(null), DayOfWeek.SUNDAY)).isTrue();
    }

    // --- Jours travaillés explicites (agents 2-3 jours par semaine) ----------

    @Test
    void jours_explicites_lundi_mercredi_vendredi() {
        // LE cas du besoin : un rythme qu'aucune valeur de JoursTravail n'exprime, et qui
        // faisait compter l'agent absent les mardis, jeudis, samedis et dimanches.
        AffectationSite a = site("PERSONNALISE", null, List.of(1, 3, 5));
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.MONDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.WEDNESDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.FRIDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.TUESDAY)).isFalse();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.THURSDAY)).isFalse();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SATURDAY)).isFalse();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    void les_jours_explicites_priment_sur_le_rythme_preregle() {
        // Contradiction impossible via l'API (la canonicalisation pose PERSONNALISE), mais
        // une écriture directe en base doit trancher dans le sens de la liste.
        AffectationSite a = site("LUN_VEN", null, List.of(0, 6));
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SATURDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.TUESDAY)).isFalse();
    }

    @Test
    void les_jours_explicites_priment_sur_le_rythme_de_l_employe() {
        AffectationSite a = site(null, null, List.of(1, 3));
        assertThat(resolver.jourOuvre(a, employe("LUN_DIM"), DayOfWeek.SUNDAY)).isFalse();
        assertThat(resolver.jourOuvre(a, employe("LUN_DIM"), DayOfWeek.WEDNESDAY)).isTrue();
    }

    @Test
    void les_jours_explicites_ignorent_le_jour_de_repos() {
        // La liste EST la semaine ouvrée : un repos y retirerait un jour explicitement coché.
        AffectationSite a = site("PERSONNALISE", 2, List.of(1, 2, 3));
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.TUESDAY)).isTrue();
    }

    @Test
    void le_7_iso_est_tolere_dans_la_liste() {
        AffectationSite a = site("PERSONNALISE", null, List.of(7));
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.MONDAY)).isFalse();
    }

    @Test
    void une_liste_vide_laisse_le_comportement_anterieur() {
        // ⚠ Vide ⇒ on retombe sur le rythme, et non « aucun jour ouvré » : mettre les jours
        // ouvrables à zéro mettrait aussi les absences à zéro.
        AffectationSite a = site("LUN_VEN", null, List.of());
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.FRIDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SATURDAY)).isFalse();
    }

    @Test
    void une_liste_nulle_laisse_le_comportement_anterieur() {
        // Garde de non-régression de tout le parc existant.
        AffectationSite a = site("LUN_SAM", null, null);
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SATURDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    void personnalise_sans_jours_ne_filtre_rien() {
        // Le marqueur promet une liste absente : on ne sait rien du rythme, donc échelon
        // permissif — jamais « aucun jour ». Le service refuse cette combinaison à l'écriture.
        AffectationSite a = site("PERSONNALISE", null, null);
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SATURDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SUNDAY)).isTrue();
    }

    @Test
    void une_liste_corrompue_retombe_sur_le_rythme() {
        // Même prudence qu'un rythme illisible : la liste est réputée absente, on ne ferme
        // pas la semaine entière — et surtout aucune exception n'est levée en lecture.
        AffectationSite a = site("LUN_VEN", null, List.of(9));
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.FRIDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.SATURDAY)).isFalse();
    }

    @Test
    void des_elements_nuls_dans_la_liste_ne_font_pas_planter() {
        AffectationSite a = site("PERSONNALISE", null, Arrays.asList(1, null, 3));
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.MONDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.WEDNESDAY)).isTrue();
        assertThat(resolver.jourOuvre(a, null, DayOfWeek.TUESDAY)).isFalse();
    }

    // --- Bout en bout : c'est ce test qui prouve la disparition du ABSENT ----

    @Test
    void prevuesPourJour_ne_rend_aucun_creneau_le_mardi() {
        // Sans créneau prévu, PointageCentraliseService.buildLignes n'émet aucune ligne :
        // ni ABSENT, ni EN_ATTENTE. C'est le mécanisme même de la correction.
        DossierEmploye e = employe(null);
        e.setAffectations(List.of(site("PERSONNALISE", null, List.of(1, 3, 5))));

        assertThat(resolver.prevuesPourJour(e, LocalDate.of(2026, 9, 8))).isEmpty();   // mardi
        assertThat(resolver.prevuesPourJour(e, LocalDate.of(2026, 9, 9))).hasSize(1);  // mercredi
    }

    @Test
    void multi_sites_seul_le_site_concerne_disparait() {
        // Règle du OU par jour : l'agent vient bien le mardi, mais sur son autre site.
        DossierEmploye e = employe(null);
        AffectationSite partiel = AffectationSite.builder()
                .site("Praline").joursTravail("PERSONNALISE").joursSemaine(List.of(1, 3, 5)).build();
        AffectationSite plein = AffectationSite.builder()
                .site("Yoff").joursTravail("LUN_VEN").build();
        e.setAffectations(List.of(partiel, plein));

        assertThat(resolver.prevuesPourJour(e, LocalDate.of(2026, 9, 8)))  // mardi
                .extracting(AffectationSite::getSite)
                .containsExactly("Yoff");
        assertThat(resolver.prevuesPourJour(e, LocalDate.of(2026, 9, 9)))  // mercredi
                .extracting(AffectationSite::getSite)
                .containsExactlyInAnyOrder("Praline", "Yoff");
    }
}
