package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Composition du solde de congés : jours pris, jours réservés par le circuit, décompte par type
 * et report des exercices antérieurs.
 *
 * <p>La <b>formule</b> de l'acquis (2 j par mois de service effectif) est pinnée à part, sur des
 * dates figées, par {@link CongeAcquisCalculatorTest}. Ici on utilise le calculateur réel et on
 * en dérive l'attendu : les assertions portent sur la composition, pas sur un nombre de jours
 * qui dépendrait du mois où la suite est exécutée.
 *
 * <p>L'employé de référence est entré le 1er janvier il y a 3 ans : son report est assez large
 * pour qu'aucun cas de test ne bute sur le plancher à 0, qui masquerait les écarts mesurés.
 */
@ExtendWith(MockitoExtension.class)
class DemandeCongeServiceSoldeTest {

    private static final String EMPLOYE = "emp-1";
    private static final LocalDate AUJOURDHUI = LocalDate.now();
    private static final int ANNEE = AUJOURDHUI.getYear();
    /** 1er janvier, il y a 3 ans → les exercices ANNEE-3, ANNEE-2 et ANNEE-1 sont pleins. */
    private static final LocalDate ENTREE = LocalDate.of(ANNEE - 3, 1, 1);

    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private DossierEmployeRepository dossierEmployeRepository;
    @Mock private CongeWorkflowService workflowService;
    @Mock private CongeIdentiteService identite;

    private final CongeAcquisCalculator calculator = new CongeAcquisCalculator();
    private DemandeCongeService service;

    /** Acquis de l'exercice courant, dérivé du calculateur réel — varie selon le mois courant. */
    private int acquisCourant;
    /** Report des 3 exercices clos, tous pleins et sans congé pris : 3 × 24 jours. */
    private int reportPlein;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(calculator, "joursAcquisParMois", 2);
        service = new DemandeCongeService(demandeCongeRepository, dossierEmployeRepository,
                new CongeMapper(), workflowService, identite, calculator);

        acquisCourant = calculator.acquis(ANNEE, ENTREE, AUJOURDHUI);
        reportPlein = calculator.acquis(ANNEE - 3, ENTREE, AUJOURDHUI)
                + calculator.acquis(ANNEE - 2, ENTREE, AUJOURDHUI)
                + calculator.acquis(ANNEE - 1, ENTREE, AUJOURDHUI);

        // Le périmètre de lecture est testé à part (DemandeCongeServiceScopeTest) : ici on
        // se place en RH pour ne mesurer que le calcul du solde.
        when(identite.perimetreLecture()).thenReturn(PerimetreConges.tout());
        employeEnBase(ENTREE);
    }

    private void employeEnBase(LocalDate dateEntree) {
        when(dossierEmployeRepository.findById(EMPLOYE)).thenReturn(Optional.of(
                DossierEmploye.builder().id(EMPLOYE).matricule("M-1").nom("Fall")
                        .dateEmbauche(dateEntree).build()));
    }

    private DemandeConge conge(StatutDemande statut, int jours) {
        return conge(statut, jours, TypeConge.ANNUEL, ANNEE);
    }

    private DemandeConge conge(StatutDemande statut, int jours, TypeConge type) {
        return conge(statut, jours, type, ANNEE);
    }

    private DemandeConge conge(StatutDemande statut, int jours, TypeConge type, int annee) {
        // 1er mars : un quantième toujours passé pour un exercice clos, et jamais à cheval
        // sur le 31/12, ce qui rendrait le rattachement à l'exercice discutable.
        LocalDate debut = LocalDate.of(annee, 3, 1);
        return DemandeConge.builder()
                .employeId(EMPLOYE)
                .statut(statut)
                .type(type)
                .nombreJours(jours)
                .dateDebut(debut)
                .dateFin(debut.plusDays(jours))
                .build();
    }

    private void congesEnBase(DemandeConge... conges) {
        when(demandeCongeRepository.findByEmployeId(eq(EMPLOYE))).thenReturn(List.of(conges));
    }

    // ─── Acquis de l'exercice courant ─────────────────────────────────────────

    @Test
    void l_acquis_vaut_deux_jours_par_mois_de_service_effectif() {
        congesEnBase();

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getAcquis()).isEqualTo(acquisCourant);
        assertThat(solde.getMoisAcquis() * 2).isEqualTo(solde.getAcquis());
        assertThat(solde.getAnneeReference()).isEqualTo(ANNEE);
    }

    @Test
    void le_report_s_ajoute_au_solde_disponible() {
        congesEnBase();

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getSoldeAnterieur()).isEqualTo(reportPlein);
        assertThat(solde.getSolde()).isEqualTo(reportPlein + acquisCourant);
    }

    @Test
    void les_demandes_approuvees_sont_comptees_comme_prises() {
        congesEnBase(conge(StatutDemande.APPROUVE, 5));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getPris()).isEqualTo(5);
        assertThat(solde.getEnCours()).isZero();
        assertThat(solde.getSolde()).isEqualTo(reportPlein + acquisCourant - 5);
    }

    @Test
    void toute_demande_du_circuit_reserve_des_jours() {
        congesEnBase(
                conge(StatutDemande.EN_ATTENTE_SUPERIEUR, 2),
                conge(StatutDemande.EN_ATTENTE_RH, 3),
                conge(StatutDemande.EN_ATTENTE_DG, 1),
                conge(StatutDemande.EN_ATTENTE, 1));       // legacy

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getEnCours()).isEqualTo(7);
        assertThat(solde.getSolde()).isEqualTo(reportPlein + acquisCourant - 7);
    }

    @Test
    void les_demandes_refusees_ou_annulees_ne_reservent_rien() {
        congesEnBase(
                conge(StatutDemande.REFUSE, 6),
                conge(StatutDemande.ANNULE, 4));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getPris()).isZero();
        assertThat(solde.getEnCours()).isZero();
        assertThat(solde.getSolde()).isEqualTo(reportPlein + acquisCourant);
    }

    // ─── Report des exercices antérieurs ──────────────────────────────────────

    @Test
    void les_conges_pris_les_annees_precedentes_amputent_le_report() {
        congesEnBase(
                conge(StatutDemande.APPROUVE, 10, TypeConge.ANNUEL, ANNEE - 2),
                conge(StatutDemande.APPROUVE, 4, TypeConge.ANNUEL, ANNEE - 1));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getSoldeAnterieur()).isEqualTo(reportPlein - 14);
        // Les congés des exercices clos ne remontent pas dans le « pris » de l'exercice courant.
        assertThat(solde.getPris()).isZero();
    }

    @Test
    void une_demande_ancienne_restee_en_attente_ne_gele_pas_de_report() {
        // Elle ne sera jamais tranchée : la geler amputerait un reliquat pour rien.
        congesEnBase(conge(StatutDemande.EN_ATTENTE_RH, 8, TypeConge.ANNUEL, ANNEE - 1));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getSoldeAnterieur()).isEqualTo(reportPlein);
        assertThat(solde.getEnCours()).isZero();
    }

    @Test
    void un_depassement_sur_un_exercice_s_impute_sur_le_reliquat_des_autres() {
        // Plancher à 0 sur le TOTAL, pas année par année : sinon le report serait surévalué.
        congesEnBase(conge(StatutDemande.APPROUVE, 40, TypeConge.ANNUEL, ANNEE - 2));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getSoldeAnterieur()).isEqualTo(reportPlein - 40);
    }

    @Test
    void le_report_ne_devient_jamais_negatif() {
        congesEnBase(conge(StatutDemande.APPROUVE, 500, TypeConge.ANNUEL, ANNEE - 1));

        assertThat(service.getSolde(EMPLOYE).getSoldeAnterieur()).isZero();
    }

    @Test
    void sans_date_d_entree_le_report_est_nul_et_l_acquis_part_de_janvier() {
        // Dossiers antérieurs : le champ n'a jamais été obligatoire. Sans base pour reconstituer
        // un historique, on ne crédite pas de reliquat inventé.
        employeEnBase(null);
        congesEnBase();

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getSoldeAnterieur()).isZero();
        assertThat(solde.getAcquis()).isEqualTo(calculator.acquis(ANNEE, null, AUJOURDHUI));
    }

    // ─── Décompte par type ────────────────────────────────────────────────────

    @Test
    void le_repos_medical_n_ampute_pas_le_solde() {
        congesEnBase(
                conge(StatutDemande.APPROUVE, 5, TypeConge.ANNUEL),
                conge(StatutDemande.APPROUVE, 10, TypeConge.REPOS_MEDICAL));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getPris()).isEqualTo(5);
        assertThat(solde.getSolde()).isEqualTo(reportPlein + acquisCourant - 5);
    }

    @Test
    void le_repos_medical_n_ampute_pas_non_plus_le_report() {
        congesEnBase(conge(StatutDemande.APPROUVE, 30, TypeConge.REPOS_MEDICAL, ANNEE - 1));

        assertThat(service.getSolde(EMPLOYE).getSoldeAnterieur()).isEqualTo(reportPlein);
    }

    @Test
    void l_absence_non_justifiee_ne_reserve_pas_de_jours() {
        congesEnBase(
                conge(StatutDemande.EN_ATTENTE_SUPERIEUR, 3, TypeConge.ANNUEL),
                conge(StatutDemande.EN_ATTENTE_RH, 4, TypeConge.ABSENCE_NON_JUSTIFIEE));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getEnCours()).isEqualTo(3);
        assertThat(solde.getSolde()).isEqualTo(reportPlein + acquisCourant - 3);
    }

    @Test
    void maternite_paternite_et_sans_solde_n_amputent_plus_le_solde() {
        // Correction d'une anomalie préexistante : ces types se retranchaient des congés payés.
        congesEnBase(
                conge(StatutDemande.APPROUVE, 90, TypeConge.MATERNITE),
                conge(StatutDemande.APPROUVE, 3, TypeConge.PATERNITE),
                conge(StatutDemande.APPROUVE, 8, TypeConge.SANS_SOLDE),
                conge(StatutDemande.APPROUVE, 2, TypeConge.EXCEPTIONNEL));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getPris()).isZero();
        assertThat(solde.getSolde()).isEqualTo(reportPlein + acquisCourant);
    }

    @Test
    void une_demande_sans_type_reste_decomptee() {
        // Données historiques : le DTO n'a jamais porté de @NotNull sur le type. On préfère
        // sous-estimer un solde que d'en créditer à tort.
        congesEnBase(conge(StatutDemande.APPROUVE, 6, null));

        assertThat(service.getSolde(EMPLOYE).getPris()).isEqualTo(6);
    }

    @Test
    void l_acquis_par_mois_est_surchargeable_par_configuration() {
        ReflectionTestUtils.setField(calculator, "joursAcquisParMois", 3);
        congesEnBase();

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getAcquis()).isEqualTo(solde.getMoisAcquis() * 3);
    }
}
