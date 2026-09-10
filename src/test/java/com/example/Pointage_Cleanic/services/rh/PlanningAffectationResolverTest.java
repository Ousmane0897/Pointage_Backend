package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;

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
        return AffectationSite.builder()
                .site("Praline")
                .joursTravail(joursTravail)
                .jourRepos(jourRepos)
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
}
