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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Invariants d'ensemble du solde : identité de l'employé reportée dans le DTO, égalité
 * {@code report + acquis - pris - enCours}, et plafonnement à 0 en cas de dépassement.
 *
 * <p>Le détail par statut et par type est couvert par {@link DemandeCongeServiceSoldeTest},
 * la formule de l'acquis par {@link CongeAcquisCalculatorTest}.
 */
@ExtendWith(MockitoExtension.class)
class DemandeCongeServiceTest {

    private static final LocalDate AUJOURDHUI = LocalDate.now();
    private static final int ANNEE = AUJOURDHUI.getYear();
    private static final LocalDate ENTREE = LocalDate.of(ANNEE - 3, 1, 1);

    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private DossierEmployeRepository dossierEmployeRepository;
    @Mock private CongeWorkflowService workflowService;
    @Mock private CongeIdentiteService identite;

    private final CongeAcquisCalculator calculator = new CongeAcquisCalculator();
    private DemandeCongeService service;

    /** Droits totaux de l'employé de référence : report des 3 exercices clos + exercice courant. */
    private int droitsTotaux;

    @BeforeEach
    void setUp() {
        // @Value n'est pas résolu hors contexte Spring : on pose la valeur à la main.
        ReflectionTestUtils.setField(calculator, "joursAcquisParMois", 2);
        // Le service a gagné trois dépendances avec le circuit de validation à 3 niveaux, le
        // périmètre de lecture et le calcul des droits : @InjectMocks les laisserait nulles et
        // getSolde partirait en NPE.
        service = new DemandeCongeService(demandeCongeRepository, dossierEmployeRepository,
                new CongeMapper(), workflowService, identite, calculator);

        droitsTotaux = calculator.acquis(ANNEE - 3, ENTREE, AUJOURDHUI)
                + calculator.acquis(ANNEE - 2, ENTREE, AUJOURDHUI)
                + calculator.acquis(ANNEE - 1, ENTREE, AUJOURDHUI)
                + calculator.acquis(ANNEE, ENTREE, AUJOURDHUI);

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
        e.setDateEmbauche(ENTREE);
        return e;
    }

    /** Congé de l'exercice courant — le 1er mars, un quantième jamais à cheval sur le 31/12. */
    private DemandeConge conge(StatutDemande statut, int jours) {
        return DemandeConge.builder()
                .statut(statut)
                .type(TypeConge.ANNUEL)
                .nombreJours(jours)
                .dateDebut(LocalDate.of(ANNEE, 3, 1))
                .build();
    }

    private void enBase(String id, DemandeConge... conges) {
        when(dossierEmployeRepository.findById(id)).thenReturn(Optional.of(employe(id)));
        when(demandeCongeRepository.findByEmployeId(eq(id))).thenReturn(List.of(conges));
    }

    @Test
    void employe_sans_conge_pris_dispose_de_la_totalite_de_ses_droits() {
        enBase("emp-1");

        SoldeCongeDto solde = service.getSolde("emp-1");

        assertThat(solde.getPris()).isZero();
        assertThat(solde.getEnCours()).isZero();
        assertThat(solde.getSolde()).isEqualTo(droitsTotaux);
        assertThat(solde.getSoldeAnterieur() + solde.getAcquis()).isEqualTo(droitsTotaux);
        assertThat(solde.getAnneeReference()).isEqualTo(ANNEE);
        assertThat(solde.getMatricule()).isEqualTo("M-emp-1");
    }

    @Test
    void solde_nominal_respecte_l_invariant_report_plus_acquis_moins_pris_moins_enCours() {
        enBase("emp-2",
                conge(StatutDemande.APPROUVE, 10),   // pris
                conge(StatutDemande.EN_ATTENTE, 5)); // enCours

        SoldeCongeDto solde = service.getSolde("emp-2");

        assertThat(solde.getPris()).isEqualTo(10);
        assertThat(solde.getEnCours()).isEqualTo(5);
        assertThat(solde.getSolde()).isEqualTo(droitsTotaux - 15);
    }

    @Test
    void solde_plafonne_a_0_en_cas_de_depassement() {
        // Le plancher masque les dépassements de droits : comportement historique, conservé.
        enBase("emp-3",
                conge(StatutDemande.APPROUVE, 500),  // très au-delà du droit
                conge(StatutDemande.EN_ATTENTE, 3));

        SoldeCongeDto solde = service.getSolde("emp-3");

        assertThat(solde.getPris()).isEqualTo(500);
        assertThat(solde.getEnCours()).isEqualTo(3);
        assertThat(solde.getSolde()).isZero();
    }
}
