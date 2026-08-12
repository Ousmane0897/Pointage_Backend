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
 * Vérifie le droit annuel de congés (22 j depuis 2026-07) et le calcul du solde,
 * y compris le plafonnement à 0 en cas de dépassement.
 */
@ExtendWith(MockitoExtension.class)
class DemandeCongeServiceTest {

    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private DossierEmployeRepository dossierEmployeRepository;
    @Mock private CongeWorkflowService workflowService;
    @Mock private CongeIdentiteService identite;

    private DemandeCongeService service;

    @BeforeEach
    void setUp() {
        // Le service a gagné deux dépendances avec le circuit de validation à 3 niveaux et le
        // périmètre de lecture : @InjectMocks les laisserait nulles et getSolde partirait en NPE.
        service = new DemandeCongeService(demandeCongeRepository, dossierEmployeRepository,
                new CongeMapper(), workflowService, identite);
        // L'acquis vient de @Value("${app.conges.jours-acquis-par-an:22}"), non résolu hors Spring.
        ReflectionTestUtils.setField(service, "joursAcquisParAn", 22);
        // Le périmètre est couvert par DemandeCongeServiceScopeTest : ici on se place en RH pour
        // ne mesurer que le calcul du solde.
        when(identite.perimetreLecture()).thenReturn(PerimetreConges.tout());
    }

    private DossierEmploye employe(String id) {
        DossierEmploye e = new DossierEmploye();
        e.setId(id);
        e.setMatricule("M-" + id);
        e.setNom("Diop");
        e.setPrenom("Awa");
        e.setDepartement("Exploitation");
        return e;
    }

    private DemandeConge conge(StatutDemande statut, int jours) {
        return DemandeConge.builder().statut(statut).nombreJours(jours).build();
    }

    @Test
    void employe_sans_conge_pris_a_22_acquis_et_22_solde() {
        when(dossierEmployeRepository.findById("emp-1")).thenReturn(Optional.of(employe("emp-1")));
        when(demandeCongeRepository.findByEmployeIdAndDateDebutBetween(eq("emp-1"), any(), any()))
                .thenReturn(List.of());

        SoldeCongeDto solde = service.getSolde("emp-1");

        assertThat(solde.getAcquis()).isEqualTo(22);
        assertThat(solde.getPris()).isZero();
        assertThat(solde.getEnCours()).isZero();
        assertThat(solde.getSolde()).isEqualTo(22);
        assertThat(solde.getAnneeReference()).isEqualTo(LocalDate.now().getYear());
        assertThat(solde.getMatricule()).isEqualTo("M-emp-1");
    }

    @Test
    void solde_nominal_respecte_l_invariant_acquis_moins_pris_moins_enCours() {
        when(dossierEmployeRepository.findById("emp-2")).thenReturn(Optional.of(employe("emp-2")));
        when(demandeCongeRepository.findByEmployeIdAndDateDebutBetween(eq("emp-2"), any(), any()))
                .thenReturn(List.of(
                        conge(StatutDemande.APPROUVE, 10),   // pris
                        conge(StatutDemande.EN_ATTENTE, 5))); // enCours

        SoldeCongeDto solde = service.getSolde("emp-2");

        assertThat(solde.getAcquis()).isEqualTo(22);
        assertThat(solde.getPris()).isEqualTo(10);
        assertThat(solde.getEnCours()).isEqualTo(5);
        assertThat(solde.getSolde()).isEqualTo(7); // 22 - 10 - 5
    }

    @Test
    void solde_plafonne_a_0_en_cas_de_depassement() {
        when(dossierEmployeRepository.findById("emp-3")).thenReturn(Optional.of(employe("emp-3")));
        when(demandeCongeRepository.findByEmployeIdAndDateDebutBetween(eq("emp-3"), any(), any()))
                .thenReturn(List.of(
                        conge(StatutDemande.APPROUVE, 25),   // déjà pris au-delà du droit
                        conge(StatutDemande.EN_ATTENTE, 3)));

        SoldeCongeDto solde = service.getSolde("emp-3");

        assertThat(solde.getAcquis()).isEqualTo(22);
        assertThat(solde.getPris()).isEqualTo(25);
        assertThat(solde.getEnCours()).isEqualTo(3);
        assertThat(solde.getSolde()).isZero(); // max(0, 22 - 25 - 3)
    }
}
