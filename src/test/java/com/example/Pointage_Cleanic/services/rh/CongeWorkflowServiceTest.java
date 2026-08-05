package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.ActionValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.exception.CongeInvalideException;
import com.example.Pointage_Cleanic.exception.CongeTransitionInterditeException;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Circuit de validation des congés : transitions, habilitations et codes d'erreur.
 *
 * <p>Le point sensible couvert ici est qu'<b>aucune décision ne doit aboutir sans que
 * l'appelant soit habilité au niveau courant</b> — l'ancien service acceptait n'importe
 * quel utilisateur authentifié sur n'importe quel statut.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CongeWorkflowServiceTest {

    private static final String MOI = "emp-moi";
    private static final String SUBORDONNE = "emp-sub";
    private static final String MOTIF_VALABLE = "Effectif insuffisant sur la période";

    @Mock private DemandeCongeRepository demandeCongeRepository;
    @Mock private DossierEmployeRepository dossierEmployeRepository;
    @Mock private CongeIdentiteService identite;
    @Mock private CongeNotificationService notificationService;
    @Mock private CongeMailNotificationService mailService;

    private CongeWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new CongeWorkflowService(demandeCongeRepository, dossierEmployeRepository,
                identite, new CongeMapper(), notificationService, mailService);

        when(demandeCongeRepository.save(any(DemandeConge.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(identite.idUtilisateurCourant()).thenReturn("usr-1");
        when(identite.nomCourant()).thenReturn("Awa Ndiaye");
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private DemandeConge demande(StatutDemande statut) {
        return DemandeConge.builder()
                .id("cg-1")
                .employeId(SUBORDONNE)
                .prenom("Moussa").nom("Fall")
                .type(TypeConge.ANNUEL)
                .dateDebut(LocalDate.of(2026, 8, 10))
                .dateFin(LocalDate.of(2026, 8, 14))
                .statut(statut)
                .superieurHierarchiqueId(MOI)
                .build();
    }

    private void chargeable(DemandeConge d) {
        when(demandeCongeRepository.findById("cg-1")).thenReturn(Optional.of(d));
    }

    /** L'appelant est le supérieur du demandeur, sans rôle particulier. */
    private void connecteSuperieur() {
        when(identite.employeIdCourant()).thenReturn(MOI);
        when(identite.estRh()).thenReturn(false);
        when(identite.estSuperAdmin()).thenReturn(false);
    }

    private void connecteRh() {
        when(identite.employeIdCourant()).thenReturn("emp-rh");
        when(identite.estRh()).thenReturn(true);
        when(identite.estSuperAdmin()).thenReturn(false);
    }

    private void connecteSuperAdmin() {
        when(identite.employeIdCourant()).thenReturn("emp-dg");
        when(identite.estRh()).thenReturn(false);
        when(identite.estSuperAdmin()).thenReturn(true);
    }

    // ─── Création ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Création")
    class Creation {

        private DemandeCongeDto payload(String employeId) {
            return DemandeCongeDto.builder()
                    .employeId(employeId)
                    .type(TypeConge.ANNUEL)
                    .dateDebut(LocalDate.of(2026, 8, 10))   // lundi
                    .dateFin(LocalDate.of(2026, 8, 21))     // vendredi suivant
                    .build();
        }

        @Test
        void demarre_au_niveau_du_superieur_et_fige_le_validateur() {
            DossierEmploye moi = DossierEmploye.builder()
                    .id(MOI).nom("Fall").prenom("Moussa").matricule("M-1")
                    .superieurHierarchiqueId("emp-chef").superieurHierarchiqueNom("Awa Ndiaye")
                    .build();
            when(identite.employeCourant()).thenReturn(Optional.of(moi));

            DemandeCongeDto res = service.creer(payload(null));

            assertThat(res.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE_SUPERIEUR);
            assertThat(res.getSuperieurHierarchiqueId()).isEqualTo("emp-chef");
            assertThat(res.getSuperieurHierarchiqueNom()).isEqualTo("Awa Ndiaye");
            assertThat(res.getNiveauSuperieurIgnore()).isFalse();
            // 2 semaines pleines = 10 jours ouvrés, pas 12 jours calendaires.
            assertThat(res.getNombreJours()).isEqualTo(10);
            assertThat(res.getHistorique()).extracting("action")
                    .containsExactly(ActionValidationConge.CREATION);
        }

        @Test
        void sans_superieur_le_circuit_demarre_a_la_rh_sans_jamais_bloquer() {
            DossierEmploye moi = DossierEmploye.builder().id(MOI).nom("Fall").build();
            when(identite.employeCourant()).thenReturn(Optional.of(moi));

            DemandeCongeDto res = service.creer(payload(null));

            assertThat(res.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE_RH);
            assertThat(res.getNiveauSuperieurIgnore()).isTrue();
            assertThat(res.getHistorique()).hasSize(2);
            assertThat(res.getHistorique().get(1).getCommentaire())
                    .contains("aucun supérieur hiérarchique");
        }

        @Test
        void deposer_pour_autrui_est_refuse_hors_rh() {
            DossierEmploye moi = DossierEmploye.builder().id(MOI).build();
            when(identite.employeCourant()).thenReturn(Optional.of(moi));
            when(identite.peutCreerPourAutrui()).thenReturn(false);

            assertThatThrownBy(() -> service.creer(payload("emp-autre")))
                    .isInstanceOf(CongeAccesRefuseException.class);
            verify(demandeCongeRepository, never()).save(any());
        }

        @Test
        void la_rh_peut_deposer_pour_autrui() {
            when(identite.employeCourant())
                    .thenReturn(Optional.of(DossierEmploye.builder().id("emp-rh").build()));
            when(identite.peutCreerPourAutrui()).thenReturn(true);
            when(dossierEmployeRepository.findById("emp-autre")).thenReturn(Optional.of(
                    DossierEmploye.builder().id("emp-autre").nom("Sow")
                            .superieurHierarchiqueId("emp-chef").build()));

            DemandeCongeDto res = service.creer(payload("emp-autre"));

            assertThat(res.getEmployeId()).isEqualTo("emp-autre");
            assertThat(res.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE_SUPERIEUR);
        }

        @Test
        void compte_non_rattache_a_un_dossier_employe_est_refuse_en_422() {
            when(identite.employeCourant()).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.creer(payload(null)))
                    .isInstanceOf(CongeInvalideException.class)
                    .hasMessageContaining("aucun dossier employé");
        }

        @Test
        void dates_inversees_refusees_en_422() {
            when(identite.employeCourant())
                    .thenReturn(Optional.of(DossierEmploye.builder().id(MOI).build()));
            DemandeCongeDto dto = payload(null);
            dto.setDateFin(LocalDate.of(2026, 8, 1));

            assertThatThrownBy(() -> service.creer(dto))
                    .isInstanceOf(CongeInvalideException.class);
        }
    }

    // ─── Transitions ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Validation séquentielle")
    class Validation {

        @Test
        void le_superieur_fait_passer_la_demande_a_la_rh() {
            DemandeConge d = demande(StatutDemande.EN_ATTENTE_SUPERIEUR);
            chargeable(d);
            connecteSuperieur();

            DemandeCongeDto res = service.valider("cg-1", "Équipe couverte");

            assertThat(res.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE_RH);
            assertThat(res.getDecisionSuperieur().getDecideurNom()).isEqualTo("Awa Ndiaye");
            assertThat(res.getDecisionSuperieur().getCommentaire()).isEqualTo("Équipe couverte");
            assertThat(res.getDecisionRh()).isNull();
            verify(mailService).notifierValidation(d, NiveauValidationConge.SUPERIEUR);
        }

        @Test
        void la_rh_fait_passer_la_demande_a_la_direction() {
            DemandeConge d = demande(StatutDemande.EN_ATTENTE_RH);
            chargeable(d);
            connecteRh();

            DemandeCongeDto res = service.valider("cg-1", null);

            assertThat(res.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE_DG);
            assertThat(res.getDecisionRh()).isNotNull();
        }

        @Test
        void la_direction_approuve_definitivement_et_renseigne_la_decision_finale() {
            DemandeConge d = demande(StatutDemande.EN_ATTENTE_DG);
            chargeable(d);
            connecteSuperAdmin();

            DemandeCongeDto res = service.valider("cg-1", "Accord");

            assertThat(res.getStatut()).isEqualTo(StatutDemande.APPROUVE);
            assertThat(res.getDecisionDg()).isNotNull();
            assertThat(res.getDateDecision()).isEqualTo(LocalDate.now());
            assertThat(res.getDecideurNom()).isEqualTo("Awa Ndiaye");
        }

        @Test
        void le_statut_legacy_en_attente_est_traite_comme_le_niveau_superieur() {
            DemandeConge d = demande(StatutDemande.EN_ATTENTE);
            chargeable(d);
            connecteSuperieur();

            DemandeCongeDto res = service.valider("cg-1", null);

            assertThat(res.getStatut()).isEqualTo(StatutDemande.EN_ATTENTE_RH);
        }
    }

    @Nested
    @DisplayName("Habilitations")
    class Habilitations {

        @Test
        void la_rh_ne_peut_pas_trancher_le_niveau_du_superieur() {
            chargeable(demande(StatutDemande.EN_ATTENTE_SUPERIEUR));
            connecteRh();

            assertThatThrownBy(() -> service.valider("cg-1", null))
                    .isInstanceOf(CongeAccesRefuseException.class);
            verify(demandeCongeRepository, never()).save(any());
        }

        @Test
        void un_superieur_ne_peut_pas_trancher_le_niveau_rh() {
            chargeable(demande(StatutDemande.EN_ATTENTE_RH));
            connecteSuperieur();

            assertThatThrownBy(() -> service.valider("cg-1", null))
                    .isInstanceOf(CongeAccesRefuseException.class);
        }

        @Test
        void un_superieur_ne_peut_trancher_que_ses_propres_subordonnes() {
            DemandeConge autre = demande(StatutDemande.EN_ATTENTE_SUPERIEUR);
            autre.setSuperieurHierarchiqueId("emp-un-autre-chef");
            chargeable(autre);
            connecteSuperieur();

            assertThatThrownBy(() -> service.valider("cg-1", null))
                    .isInstanceOf(CongeAccesRefuseException.class);
        }

        @Test
        void le_super_admin_agit_a_tous_les_niveaux() {
            connecteSuperAdmin();

            for (StatutDemande statut : List.of(StatutDemande.EN_ATTENTE_SUPERIEUR,
                    StatutDemande.EN_ATTENTE_RH, StatutDemande.EN_ATTENTE_DG)) {
                chargeable(demande(statut));
                assertThat(service.valider("cg-1", null).getStatut()).isNotNull();
            }
        }

        @Test
        void seul_le_niveau_direction_est_reserve_au_super_admin() {
            chargeable(demande(StatutDemande.EN_ATTENTE_DG));
            connecteRh();

            assertThatThrownBy(() -> service.valider("cg-1", null))
                    .isInstanceOf(CongeAccesRefuseException.class);
        }
    }

    @Nested
    @DisplayName("Refus")
    class Refus {

        @Test
        void le_refus_est_terminal_et_trace_le_niveau() {
            DemandeConge d = demande(StatutDemande.EN_ATTENTE_RH);
            chargeable(d);
            connecteRh();

            DemandeCongeDto res = service.refuser("cg-1", MOTIF_VALABLE);

            assertThat(res.getStatut()).isEqualTo(StatutDemande.REFUSE);
            assertThat(res.getNiveauRefus()).isEqualTo(NiveauValidationConge.RH);
            assertThat(res.getMotifRefus()).isEqualTo(MOTIF_VALABLE);
            verify(mailService).notifierRefus(d, NiveauValidationConge.RH, MOTIF_VALABLE);
        }

        @Test
        void un_motif_trop_court_est_refuse_en_422_avant_tout_chargement() {
            assertThatThrownBy(() -> service.refuser("cg-1", "trop"))
                    .isInstanceOf(CongeInvalideException.class);
            verify(demandeCongeRepository, never()).findById(any());
        }

        @Test
        void un_motif_absent_est_refuse_en_422() {
            assertThatThrownBy(() -> service.refuser("cg-1", null))
                    .isInstanceOf(CongeInvalideException.class);
        }

        @Test
        void refuser_exige_la_meme_habilitation_que_valider() {
            chargeable(demande(StatutDemande.EN_ATTENTE_SUPERIEUR));
            connecteRh();

            assertThatThrownBy(() -> service.refuser("cg-1", MOTIF_VALABLE))
                    .isInstanceOf(CongeAccesRefuseException.class);
        }
    }

    @Nested
    @DisplayName("Statuts terminaux")
    class Terminaux {

        @Test
        void aucune_decision_sur_une_demande_deja_close() {
            connecteSuperAdmin();

            for (StatutDemande statut : List.of(StatutDemande.APPROUVE, StatutDemande.REFUSE,
                    StatutDemande.ANNULE)) {
                chargeable(demande(statut));
                assertThatThrownBy(() -> service.valider("cg-1", null))
                        .isInstanceOf(CongeTransitionInterditeException.class);
                assertThatThrownBy(() -> service.refuser("cg-1", MOTIF_VALABLE))
                        .isInstanceOf(CongeTransitionInterditeException.class);
            }
        }

        @Test
        void annuler_une_demande_close_est_un_conflit() {
            chargeable(demande(StatutDemande.APPROUVE));
            connecteSuperAdmin();

            assertThatThrownBy(() -> service.annuler("cg-1"))
                    .isInstanceOf(CongeTransitionInterditeException.class);
        }
    }

    @Nested
    @DisplayName("Annulation")
    class Annulation {

        @Test
        void le_demandeur_annule_sa_propre_demande() {
            DemandeConge d = demande(StatutDemande.EN_ATTENTE_SUPERIEUR);
            chargeable(d);
            when(identite.employeIdCourant()).thenReturn(SUBORDONNE);
            when(identite.peutCreerPourAutrui()).thenReturn(false);

            service.annuler("cg-1");

            assertThat(d.getStatut()).isEqualTo(StatutDemande.ANNULE);
        }

        @Test
        void un_tiers_sans_droit_rh_ne_peut_pas_annuler() {
            chargeable(demande(StatutDemande.EN_ATTENTE_SUPERIEUR));
            when(identite.employeIdCourant()).thenReturn("emp-tiers");
            when(identite.peutCreerPourAutrui()).thenReturn(false);

            assertThatThrownBy(() -> service.annuler("cg-1"))
                    .isInstanceOf(CongeAccesRefuseException.class);
        }
    }

    // ─── Décoration pour le front ─────────────────────────────────────────────

    @Nested
    @DisplayName("peutValiderParMoi")
    class Decoration {

        @Test
        void vrai_pour_le_superieur_du_demandeur() {
            connecteSuperieur();
            DemandeCongeDto dto = DemandeCongeDto.builder()
                    .statut(StatutDemande.EN_ATTENTE_SUPERIEUR)
                    .superieurHierarchiqueId(MOI)
                    .build();

            assertThat(service.decorer(dto).getPeutValiderParMoi()).isTrue();
        }

        @Test
        void faux_pour_un_autre_encadrant() {
            connecteSuperieur();
            DemandeCongeDto dto = DemandeCongeDto.builder()
                    .statut(StatutDemande.EN_ATTENTE_SUPERIEUR)
                    .superieurHierarchiqueId("emp-un-autre-chef")
                    .build();

            assertThat(service.decorer(dto).getPeutValiderParMoi()).isFalse();
        }

        @Test
        void faux_sur_un_statut_terminal() {
            connecteSuperAdmin();
            DemandeCongeDto dto = DemandeCongeDto.builder().statut(StatutDemande.APPROUVE).build();

            assertThat(service.decorer(dto).getPeutValiderParMoi()).isFalse();
        }
    }
}
