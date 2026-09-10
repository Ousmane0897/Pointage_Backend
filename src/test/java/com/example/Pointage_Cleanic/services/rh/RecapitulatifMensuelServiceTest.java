package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.RecapitulatifMensuelDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.rh.HeureSupplementaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Récapitulatif mensuel — jours ouvrables par employé, fériés et absences.
 *
 * <p>Fenêtre figée : <b>septembre 2026</b>, 30 jours du mardi 1er au mercredi 30, soit
 * 22 jours ouvrés lundi-vendredi et 26 lundi-samedi.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecapitulatifMensuelServiceTest {

    @Mock private DossierEmployeRepository dossierEmployeRepository;
    @Mock private PointageRepository pointageRepository;
    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private HeureSupplementaireRepository heureSupplementaireRepository;
    @Mock private JourFerieService jourFerieService;

    private RecapitulatifMensuelService service;

    private static final LocalDate FERIE_MERCREDI = LocalDate.of(2026, 9, 16);

    @BeforeEach
    void setUp() {
        service = new RecapitulatifMensuelService(
                dossierEmployeRepository, pointageRepository, demandeCongeRepository,
                heureSupplementaireRepository,
                new CalendrierTravailService(new PlanningAffectationResolver()),
                jourFerieService);

        when(jourFerieService.datesFeriees(any(), any())).thenReturn(Set.of());
        when(heureSupplementaireRepository.findByStatutAndDateBetween(any(), any(), any()))
                .thenReturn(List.of());
        when(demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of());
        when(pointageRepository.findByDateBetween(any(), any())).thenReturn(List.of());
    }

    private static DossierEmploye employe(String id, String rythme) {
        DossierEmploye e = new DossierEmploye();
        e.setId(id);
        e.setAgentId(id);
        e.setMatricule("M-" + id);
        e.setNom("Diop");
        e.setPrenom("Awa");
        e.setStatut(StatutDossierEmploye.ACTIF);
        e.setAffectations(new ArrayList<>(List.of(
                AffectationSite.builder().site("Yoff").joursTravail(rythme).build())));
        return e;
    }

    private static Pointage pointage(String agentId, LocalDate date) {
        Pointage p = new Pointage();
        p.setCodeSecret(agentId);
        p.setDate(date);
        return p;
    }

    private RecapitulatifMensuelDto recap(DossierEmploye e) {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(e));
        List<RecapitulatifMensuelDto> lignes =
                service.getRecapitulatifDetaille(9, 2026, null, null, null);
        assertThat(lignes).hasSize(1);
        return lignes.get(0);
    }

    // --- Jours ouvrables par rythme -------------------------------------------

    @Test
    void un_agent_lundi_vendredi_a_22_jours_ouvrables() {
        assertThat(recap(employe("a1", "LUN_VEN")).getJoursOuvrables()).isEqualTo(22);
    }

    @Test
    void un_agent_de_terrain_lundi_samedi_a_26_jours_ouvrables() {
        // ⚠ C'est LE bug corrigé : le calcul lundi-vendredi en dur ignorait ses 4 samedis,
        // et ses absences étaient donc systématiquement fausses.
        assertThat(recap(employe("a2", "LUN_SAM")).getJoursOuvrables()).isEqualTo(26);
    }

    // --- Jours fériés ----------------------------------------------------------

    @Test
    void un_ferie_sort_des_jours_ouvrables_sans_creer_d_absence() {
        when(jourFerieService.datesFeriees(any(), any())).thenReturn(Set.of(FERIE_MERCREDI));

        RecapitulatifMensuelDto ligne = recap(employe("a3", "LUN_VEN"));

        assertThat(ligne.getJoursOuvrables()).isEqualTo(21);
        assertThat(ligne.getJoursFeries()).isEqualTo(1);
        assertThat(ligne.getJoursTravaillesFeries()).isZero();
        // 21 dus, 0 pointé : 21 absences — le férié n'en fait pas une 22e.
        assertThat(ligne.getJoursAbsence()).isEqualTo(21);
    }

    @Test
    void un_ferie_travaille_est_compte_comme_jour_travaille_sans_fausser_les_absences() {
        when(jourFerieService.datesFeriees(any(), any())).thenReturn(Set.of(FERIE_MERCREDI));
        when(pointageRepository.findByDateBetween(any(), any()))
                .thenReturn(List.of(pointage("a4", FERIE_MERCREDI)));

        RecapitulatifMensuelDto ligne = recap(employe("a4", "LUN_VEN"));

        // ⚠ Deux compteurs distincts : le travail réel se voit (1 jour travaillé) sans
        // entrer dans le calcul des absences, qui ne porte que sur les jours dus (21).
        assertThat(ligne.getJoursTravailles()).isEqualTo(1);
        assertThat(ligne.getJoursTravaillesFeries()).isEqualTo(1);
        assertThat(ligne.getJoursOuvrables()).isEqualTo(21);
        assertThat(ligne.getJoursAbsence()).isEqualTo(21);
    }

    // --- Congés ----------------------------------------------------------------

    @Test
    void un_conge_a_cheval_sur_deux_mois_n_est_compte_que_pour_sa_part_du_mois() {
        // ⚠ Correction : la somme des `nombreJours` comptait la demande EN ENTIER dans
        // chacun des deux mois, ce qui pouvait ramener les absences à zéro et masquer
        // une absence réelle.
        DemandeConge c = new DemandeConge();
        c.setEmployeId("a5");
        c.setDateDebut(LocalDate.of(2026, 8, 24));
        c.setDateFin(LocalDate.of(2026, 9, 4));
        c.setNombreJours(10);
        when(demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(c));

        RecapitulatifMensuelDto ligne = recap(employe("a5", "LUN_VEN"));

        // Du mardi 1er au vendredi 4 septembre : 4 jours ouvrés, pas 10.
        assertThat(ligne.getJoursConge()).isEqualTo(4);
        assertThat(ligne.getJoursAbsence()).isEqualTo(18);
    }

    @Test
    void un_jour_a_la_fois_pointe_et_en_conge_n_est_pas_decompte_deux_fois() {
        LocalDate jeudi3 = LocalDate.of(2026, 9, 3);
        DemandeConge c = new DemandeConge();
        c.setEmployeId("a6");
        c.setDateDebut(jeudi3);
        c.setDateFin(jeudi3);
        when(demandeCongeRepository
                .findByStatutAndDateDebutLessThanEqualAndDateFinGreaterThanEqual(any(), any(), any()))
                .thenReturn(List.of(c));
        when(pointageRepository.findByDateBetween(any(), any()))
                .thenReturn(List.of(pointage("a6", jeudi3)));

        RecapitulatifMensuelDto ligne = recap(employe("a6", "LUN_VEN"));

        // 22 dus, 1 seul jour couvert (et non 2) ⇒ 21 absences, jamais 20.
        assertThat(ligne.getJoursAbsence()).isEqualTo(21);
    }

    // --- Discipline de lecture -------------------------------------------------

    @Test
    void le_calendrier_des_feries_n_est_lu_qu_une_fois_pour_tout_le_tableau() {
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(
                employe("a7", "LUN_VEN"), employe("a8", "LUN_SAM"), employe("a9", "LUN_DIM")));

        service.getRecapitulatifDetaille(9, 2026, null, null, null);

        // ⚠ Une lecture par employé serait N requêtes Mongo pour une valeur identique, et
        // deux lignes du tableau pourraient reposer sur des calendriers différents si
        // l'exécution chevauchait une saisie RH.
        verify(jourFerieService, times(1)).datesFeriees(any(), any());
    }
}
