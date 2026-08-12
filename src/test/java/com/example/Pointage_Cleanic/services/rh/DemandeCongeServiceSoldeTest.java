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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Calcul du solde de congés.
 *
 * <p>Deux corrections couvertes ici : l'acquis annuel est de <b>22 jours ouvrés</b> (et non
 * 30), et <b>toute demande encore dans le circuit</b> réserve des jours — pas seulement
 * celles au premier niveau, sinon une demande en cours de validation disparaîtrait du solde.
 */
@ExtendWith(MockitoExtension.class)
class DemandeCongeServiceSoldeTest {

    private static final String EMPLOYE = "emp-1";

    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private DossierEmployeRepository dossierEmployeRepository;
    @Mock private CongeWorkflowService workflowService;
    @Mock private CongeIdentiteService identite;

    private DemandeCongeService service;

    @BeforeEach
    void setUp() {
        service = new DemandeCongeService(demandeCongeRepository, dossierEmployeRepository,
                new CongeMapper(), workflowService, identite);
        ReflectionTestUtils.setField(service, "joursAcquisParAn", 22);
        // Le périmètre de lecture est testé à part (DemandeCongeServiceScopeTest) : ici on
        // se place en RH pour ne mesurer que le calcul du solde.
        when(identite.perimetreLecture()).thenReturn(PerimetreConges.tout());

        when(dossierEmployeRepository.findById(EMPLOYE)).thenReturn(Optional.of(
                DossierEmploye.builder().id(EMPLOYE).matricule("M-1").nom("Fall").build()));
    }

    private DemandeConge conge(StatutDemande statut, int jours) {
        return conge(statut, jours, TypeConge.ANNUEL);
    }

    private DemandeConge conge(StatutDemande statut, int jours, TypeConge type) {
        return DemandeConge.builder()
                .employeId(EMPLOYE)
                .statut(statut)
                .type(type)
                .nombreJours(jours)
                .dateDebut(LocalDate.now())
                .dateFin(LocalDate.now().plusDays(jours))
                .build();
    }

    private void congesEnBase(DemandeConge... conges) {
        when(demandeCongeRepository.findByEmployeIdAndDateDebutBetween(eq(EMPLOYE), any(), any()))
                .thenReturn(List.of(conges));
    }

    @Test
    void acquis_annuel_de_22_jours() {
        congesEnBase();

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getAcquis()).isEqualTo(22);
        assertThat(solde.getSolde()).isEqualTo(22);
    }

    @Test
    void les_demandes_approuvees_sont_comptees_comme_prises() {
        congesEnBase(conge(StatutDemande.APPROUVE, 5));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getPris()).isEqualTo(5);
        assertThat(solde.getEnCours()).isZero();
        assertThat(solde.getSolde()).isEqualTo(17);
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
        assertThat(solde.getSolde()).isEqualTo(15);
    }

    @Test
    void les_demandes_refusees_ou_annulees_ne_reservent_rien() {
        congesEnBase(
                conge(StatutDemande.REFUSE, 6),
                conge(StatutDemande.ANNULE, 4));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getPris()).isZero();
        assertThat(solde.getEnCours()).isZero();
        assertThat(solde.getSolde()).isEqualTo(22);
    }

    // ─── Décompte par type ────────────────────────────────────────────────────

    @Test
    void le_repos_medical_n_ampute_pas_le_solde() {
        congesEnBase(
                conge(StatutDemande.APPROUVE, 5, TypeConge.ANNUEL),
                conge(StatutDemande.APPROUVE, 10, TypeConge.REPOS_MEDICAL));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getPris()).isEqualTo(5);
        assertThat(solde.getSolde()).isEqualTo(17);
    }

    @Test
    void l_absence_non_justifiee_ne_reserve_pas_de_jours() {
        congesEnBase(
                conge(StatutDemande.EN_ATTENTE_SUPERIEUR, 3, TypeConge.ANNUEL),
                conge(StatutDemande.EN_ATTENTE_RH, 4, TypeConge.ABSENCE_NON_JUSTIFIEE));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getEnCours()).isEqualTo(3);
        assertThat(solde.getSolde()).isEqualTo(19);
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
        assertThat(solde.getSolde()).isEqualTo(22);
    }

    @Test
    void une_demande_sans_type_reste_decomptee() {
        // Données historiques : le DTO n'a jamais porté de @NotNull sur le type. On préfère
        // sous-estimer un solde que d'en créditer à tort.
        congesEnBase(conge(StatutDemande.APPROUVE, 6, null));

        assertThat(service.getSolde(EMPLOYE).getPris()).isEqualTo(6);
    }

    @Test
    void l_acquis_est_surchargeable_par_configuration() {
        ReflectionTestUtils.setField(service, "joursAcquisParAn", 24);
        congesEnBase(conge(StatutDemande.APPROUVE, 4));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getAcquis()).isEqualTo(24);
        assertThat(solde.getSolde()).isEqualTo(20);
    }
}
