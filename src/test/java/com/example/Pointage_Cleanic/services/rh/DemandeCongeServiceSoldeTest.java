package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
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

    private DemandeCongeService service;

    @BeforeEach
    void setUp() {
        service = new DemandeCongeService(demandeCongeRepository, dossierEmployeRepository,
                new CongeMapper(), workflowService);
        ReflectionTestUtils.setField(service, "joursAcquisParAn", 22);

        when(dossierEmployeRepository.findById(EMPLOYE)).thenReturn(Optional.of(
                DossierEmploye.builder().id(EMPLOYE).matricule("M-1").nom("Fall").build()));
    }

    private DemandeConge conge(StatutDemande statut, int jours) {
        return DemandeConge.builder()
                .employeId(EMPLOYE)
                .statut(statut)
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

    @Test
    void l_acquis_est_surchargeable_par_configuration() {
        ReflectionTestUtils.setField(service, "joursAcquisParAn", 24);
        congesEnBase(conge(StatutDemande.APPROUVE, 4));

        SoldeCongeDto solde = service.getSolde(EMPLOYE);

        assertThat(solde.getAcquis()).isEqualTo(24);
        assertThat(solde.getSolde()).isEqualTo(20);
    }
}
