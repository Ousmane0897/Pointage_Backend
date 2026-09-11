package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Jours ouvrables par employé — rythme, jour de repos et jours fériés combinés.
 *
 * <p>Dates figées sur <b>septembre 2026</b> : 30 jours, du mardi 1er au mercredi 30. Il
 * compte 4 samedis (5, 12, 19, 26) et 4 dimanches (6, 13, 20, 27), donc <b>22 jours
 * ouvrés lundi-vendredi</b> et 26 jours lundi-samedi.
 */
class CalendrierTravailServiceTest {

    private final CalendrierTravailService calendrier =
            new CalendrierTravailService(new PlanningAffectationResolver());

    private static final LocalDate DEBUT = LocalDate.of(2026, 9, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 9, 30);

    /** Mercredi 16 septembre 2026 — un jour ouvré pour tous les rythmes. */
    private static final LocalDate FERIE_MERCREDI = LocalDate.of(2026, 9, 16);
    /** Dimanche 20 septembre 2026. */
    private static final LocalDate FERIE_DIMANCHE = LocalDate.of(2026, 9, 20);

    private static DossierEmploye employe(AffectationSite... affectations) {
        DossierEmploye e = new DossierEmploye();
        e.setAffectations(new java.util.ArrayList<>(List.of(affectations)));
        return e;
    }

    private static AffectationSite site(String nom, String joursTravail, Integer jourRepos) {
        return AffectationSite.builder()
                .site(nom)
                .joursTravail(joursTravail)
                .jourRepos(jourRepos)
                .build();
    }

    private int compte(DossierEmploye e, Set<LocalDate> feries) {
        return calendrier.joursOuvrables(e, DEBUT, FIN, feries).size();
    }

    // --- Sans férié : le rythme seul -----------------------------------------

    @Test
    void back_office_lundi_vendredi_compte_22_jours() {
        assertThat(compte(employe(site("Siège", "LUN_VEN", null)), Set.of())).isEqualTo(22);
    }

    @Test
    void agent_de_terrain_lundi_samedi_compte_26_jours() {
        // C'est le bug d'origine : ces 4 samedis étaient purement ignorés du récapitulatif.
        assertThat(compte(employe(site("Yoff", "LUN_SAM", null)), Set.of())).isEqualTo(26);
    }

    @Test
    void un_jour_de_repos_hors_dimanche_retire_ce_jour_le_cas_praline() {
        // Restaurant ouvert 7j/7, repos le mardi — septembre 2026 en compte 5 (1, 8, 15,
        // 22, 29), d'où 30 − 5 = 25.
        assertThat(compte(employe(site("Praline", "LUN_DIM", 2)), Set.of())).isEqualTo(25);
    }

    // --- Multi-sites : la règle est le OU ------------------------------------

    @Test
    void un_agent_bi_site_travaille_des_qu_un_seul_site_l_attend() {
        // ⚠ LUN_VEN sur un site, LUN_SAM sur l'autre : il travaille bien le samedi.
        DossierEmploye e = employe(site("Siège", "LUN_VEN", null), site("Yoff", "LUN_SAM", null));
        assertThat(compte(e, Set.of())).isEqualTo(26);
        assertThat(calendrier.jourOuvrable(e, LocalDate.of(2026, 9, 5), Set.of())).isTrue();
    }

    @Test
    void le_jour_est_compte_une_fois_et_non_par_site() {
        // Le récapitulatif compte des JOURS, pas des créneaux : deux sites le même jour
        // ne font pas deux jours ouvrables.
        DossierEmploye e = employe(site("Siège", "LUN_VEN", null), site("Yoff", "LUN_VEN", null));
        assertThat(compte(e, Set.of())).isEqualTo(22);
    }

    // --- Jours fériés ---------------------------------------------------------

    @Test
    void un_ferie_tombant_un_jour_travaille_sort_des_jours_ouvrables() {
        DossierEmploye e = employe(site("Siège", "LUN_VEN", null));
        assertThat(compte(e, Set.of(FERIE_MERCREDI))).isEqualTo(21);
        assertThat(calendrier.jourOuvrable(e, FERIE_MERCREDI, Set.of(FERIE_MERCREDI))).isFalse();
    }

    @Test
    void un_ferie_tombant_un_jour_non_travaille_ne_change_rien() {
        // Le dimanche n'était déjà pas ouvrable : le férié ne retire rien de plus.
        DossierEmploye e = employe(site("Siège", "LUN_VEN", null));
        assertThat(compte(e, Set.of(FERIE_DIMANCHE))).isEqualTo(22);
    }

    @Test
    void un_ferie_tombant_le_jour_de_repos_d_un_agent_ne_lui_retire_rien() {
        LocalDate ferieMardi = LocalDate.of(2026, 9, 15);
        DossierEmploye praline = employe(site("Praline", "LUN_DIM", 2));
        assertThat(compte(praline, Set.of(ferieMardi))).isEqualTo(25);

        // Le même férié retire bien un jour à un agent qui travaille le mardi.
        assertThat(compte(employe(site("Siège", "LUN_VEN", null)), Set.of(ferieMardi))).isEqualTo(21);
    }

    @Test
    void les_feries_travailles_sont_ceux_qui_seraient_tombes_un_jour_ouvre() {
        DossierEmploye e = employe(site("Siège", "LUN_VEN", null));
        Set<LocalDate> feries = Set.of(FERIE_MERCREDI, FERIE_DIMANCHE);

        // Le férié du dimanche ne figure pas dans sa ligne : il ne lui fait rien gagner.
        assertThat(calendrier.feriesTravailles(e, DEBUT, FIN, feries))
                .containsExactly(FERIE_MERCREDI);
    }

    @Test
    void un_calendrier_de_feries_vide_ou_absent_reproduit_le_comportement_anterieur() {
        DossierEmploye e = employe(site("Yoff", "LUN_SAM", null));
        assertThat(compte(e, Set.of())).isEqualTo(26);
        assertThat(compte(e, null)).isEqualTo(26);
        assertThat(calendrier.estFerie(FERIE_MERCREDI, null)).isFalse();
    }

    // --- Jours travaillés explicites (agents 2-3 jours par semaine) ----------

    /** Affectation à jours explicites, marqueur compris. */
    private static AffectationSite sitePersonnalise(String nom, List<Integer> jours) {
        return AffectationSite.builder()
                .site(nom).joursTravail("PERSONNALISE").joursSemaine(jours).build();
    }

    @Test
    void un_agent_trois_jours_par_semaine_ne_compte_que_ses_jours() {
        // Septembre 2026 : 4 lundis (7, 14, 21, 28), 5 mercredis (2, 9, 16, 23, 30) et
        // 4 vendredis (4, 11, 18, 25) — soit 13 jours dus au lieu des 22 de LUN_VEN. Ce
        // sont exactement les 9 jours d'écart qui étaient comptés comme des absences.
        assertThat(compte(employe(sitePersonnalise("Praline", List.of(1, 3, 5))), Set.of()))
                .isEqualTo(13);
    }

    @Test
    void un_agent_deux_jours_par_semaine_ne_compte_que_ses_jours() {
        // Mardis (1, 8, 15, 22, 29) + jeudis (3, 10, 17, 24) = 9.
        assertThat(compte(employe(sitePersonnalise("Praline", List.of(2, 4))), Set.of()))
                .isEqualTo(9);
    }

    @Test
    void un_ferie_tombant_sur_un_jour_explicite_le_retire() {
        // Le mercredi 16 est dû : 13 − 1 = 12. Un férié n'est jamais ouvrable.
        assertThat(compte(employe(sitePersonnalise("Praline", List.of(1, 3, 5))),
                Set.of(FERIE_MERCREDI))).isEqualTo(12);
    }

    @Test
    void un_ferie_hors_des_jours_explicites_ne_change_rien() {
        // Le dimanche 20 n'était pas dû : le retirer serait le décompter deux fois.
        assertThat(compte(employe(sitePersonnalise("Praline", List.of(1, 3, 5))),
                Set.of(FERIE_DIMANCHE))).isEqualTo(13);
    }

    @Test
    void feriesTravailles_ignore_un_ferie_hors_des_jours_explicites() {
        DossierEmploye e = employe(sitePersonnalise("Praline", List.of(1, 3, 5)));
        assertThat(calendrier.feriesTravailles(e, DEBUT, FIN,
                Set.of(FERIE_MERCREDI, FERIE_DIMANCHE)))
                .containsExactly(FERIE_MERCREDI);
    }

    @Test
    void multi_sites_la_regle_reste_le_ou_avec_des_jours_explicites() {
        // 3 j/semaine sur un site + LUN_VEN sur l'autre : l'agent doit bien 22 jours, ses
        // mardis et jeudis étant honorés ailleurs.
        DossierEmploye e = employe(
                sitePersonnalise("Praline", List.of(1, 3, 5)),
                site("Siège", "LUN_VEN", null));
        assertThat(compte(e, Set.of())).isEqualTo(22);
    }

    @Test
    void la_periode_de_presence_s_applique_aussi_aux_jours_explicites() {
        AffectationSite arriveeEnCoursDeMois = AffectationSite.builder()
                .site("Praline").joursTravail("PERSONNALISE").joursSemaine(List.of(1, 3, 5))
                .dateEntree(LocalDate.of(2026, 9, 16))
                .build();
        // Du mercredi 16 au 30 : mercredis 16, 23, 30 + vendredis 18, 25 + lundis 21, 28 = 7.
        assertThat(compte(employe(arriveeEnCoursDeMois), Set.of())).isEqualTo(7);
    }

    // --- Replis prudents ------------------------------------------------------

    @Test
    void un_dossier_sans_affectation_reste_repute_travaille() {
        // ⚠ Le déclarer non ouvrable mettrait ses jours ouvrables à zéro, donc ses absences
        // à zéro — le faux négatif que le résolveur refuse déjà.
        DossierEmploye e = employe();
        assertThat(compte(e, Set.of())).isEqualTo(30);
    }

    @Test
    void un_rythme_absent_ne_filtre_rien() {
        assertThat(compte(employe(site("Yoff", null, null)), Set.of())).isEqualTo(30);
    }

    @Test
    void la_periode_de_presence_sur_le_site_est_respectee() {
        AffectationSite arriveeEnCoursDeMois = AffectationSite.builder()
                .site("Yoff")
                .joursTravail("LUN_VEN")
                .dateEntree(LocalDate.of(2026, 9, 16))
                .build();
        // Du mercredi 16 au mercredi 30 : 11 jours ouvrés.
        assertThat(compte(employe(arriveeEnCoursDeMois), Set.of())).isEqualTo(11);
    }

    @Test
    void bornes_absentes_ou_inversees_donnent_une_liste_vide() {
        DossierEmploye e = employe(site("Siège", "LUN_VEN", null));
        assertThat(calendrier.joursOuvrables(e, FIN, DEBUT, Set.of())).isEmpty();
        assertThat(calendrier.joursOuvrables(e, null, FIN, Set.of())).isEmpty();
        assertThat(calendrier.joursOuvrables(null, DEBUT, FIN, Set.of())).isEmpty();
    }
}
