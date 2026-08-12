package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Périmètre de visibilité appliqué aux demandes de congé et aux soldes.
 *
 * <p>Un agent ne voit que ses congés, son supérieur direct voit en plus ceux de ses
 * subordonnés, la RH et la Direction générale voient tout. Le filtrage est fait
 * <b>serveur</b> : le front n'est qu'une commodité d'affichage.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DemandeCongeServiceScopeTest {

    private static final String MOI = "emp-moi";
    private static final String SUBORDONNE = "emp-sub";
    private static final String TIERS = "emp-tiers";

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
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private void perimetre(PerimetreConges perimetre) {
        when(identite.perimetreLecture()).thenReturn(perimetre);
    }

    /** Périmètre d'un manager : lui-même + un subordonné direct. */
    private PerimetreConges perimetreManager() {
        return new PerimetreConges(false, MOI, Set.of(MOI, SUBORDONNE));
    }

    private DemandeConge demande(String employeId, String superieurId) {
        return DemandeConge.builder()
                .id("dem-" + employeId)
                .employeId(employeId)
                .superieurHierarchiqueId(superieurId)
                .type(TypeConge.ANNUEL)
                .statut(StatutDemande.EN_ATTENTE_SUPERIEUR)
                .dateDebut(LocalDate.of(2026, 3, 2))
                .dateFin(LocalDate.of(2026, 3, 6))
                .nombreJours(5)
                .dateDemande(LocalDate.of(2026, 3, 1))
                .build();
    }

    private DemandeConge demandeDatee(String id, LocalDate dateDemande) {
        DemandeConge d = demande(SUBORDONNE, MOI);
        d.setId(id);
        d.setDateDemande(dateDemande);
        return d;
    }

    private void demandesEnBase(DemandeConge... demandes) {
        when(demandeCongeRepository.findAll()).thenReturn(List.of(demandes));
    }

    private Page<DemandeCongeDto> rechercher() {
        return service.searchDemandes(null, null, null, null, null, null, null, null, 0, 10);
    }

    // ─── searchDemandes ───────────────────────────────────────────────────────

    @Test
    void un_agent_ne_voit_que_ses_propres_demandes() {
        perimetre(new PerimetreConges(false, MOI, Set.of(MOI)));
        demandesEnBase(demande(MOI, "emp-chef"), demande(TIERS, "emp-chef"));

        Page<DemandeCongeDto> page = rechercher();

        assertThat(page.getContent()).extracting(DemandeCongeDto::getEmployeId).containsExactly(MOI);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void un_superieur_voit_aussi_les_demandes_de_ses_subordonnes() {
        perimetre(perimetreManager());
        demandesEnBase(demande(MOI, "emp-chef"), demande(SUBORDONNE, MOI), demande(TIERS, "emp-chef"));

        Page<DemandeCongeDto> page = rechercher();

        assertThat(page.getContent()).extracting(DemandeCongeDto::getEmployeId)
                .containsExactlyInAnyOrder(MOI, SUBORDONNE);
    }

    @Test
    void la_rh_voit_toutes_les_demandes() {
        perimetre(PerimetreConges.tout());
        demandesEnBase(demande(MOI, "emp-chef"), demande(TIERS, "emp-autre"));

        assertThat(rechercher().getTotalElements()).isEqualTo(2);
    }

    @Test
    void le_validateur_fige_voit_la_demande_meme_hors_organigramme_courant() {
        // Réorg : l'ancien subordonné a changé de manager, mais la demande en vol est
        // toujours à trancher par le supérieur figé à sa création.
        perimetre(new PerimetreConges(false, MOI, Set.of(MOI)));
        demandesEnBase(demande("emp-ancien-subordonne", MOI));

        assertThat(rechercher().getContent())
                .extracting(DemandeCongeDto::getEmployeId).containsExactly("emp-ancien-subordonne");
    }

    @Test
    void le_total_de_pagination_reflete_le_perimetre() {
        // Filtrer après la pagination donnerait des pages partiellement vides et un
        // compteur faux : le filtre doit passer avant le découpage.
        perimetre(new PerimetreConges(false, MOI, Set.of(MOI)));
        demandesEnBase(
                demande(MOI, "emp-chef"),
                demande(TIERS, "emp-chef"),
                demande("emp-tiers-2", "emp-chef"));

        Page<DemandeCongeDto> page = rechercher();

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void un_compte_non_rattache_ne_voit_aucune_demande() {
        perimetre(PerimetreConges.vide());

        Page<DemandeCongeDto> page = rechercher();

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        // Périmètre vide : on n'interroge même pas la base.
        org.mockito.Mockito.verify(demandeCongeRepository, org.mockito.Mockito.never()).findAll();
    }

    // ─── getByEmployeId / getSolde ────────────────────────────────────────────

    @Test
    void consulter_les_demandes_d_un_tiers_est_refuse() {
        perimetre(perimetreManager());

        assertThatThrownBy(() -> service.getByEmployeId(TIERS))
                .isInstanceOf(CongeAccesRefuseException.class);
    }

    @Test
    void l_historique_d_un_employe_revient_du_plus_recent_au_plus_ancien() {
        // Ordre attendu par l'onglet Congés de la fiche employé ; une date de demande
        // absente (données historiques) est reléguée en fin de liste.
        perimetre(perimetreManager());
        when(demandeCongeRepository.findByEmployeId(SUBORDONNE)).thenReturn(List.of(
                demandeDatee("vieille", LocalDate.of(2026, 1, 5)),
                demandeDatee("sans-date", null),
                demandeDatee("recente", LocalDate.of(2026, 7, 20))));

        assertThat(service.getByEmployeId(SUBORDONNE))
                .extracting(DemandeCongeDto::getId)
                .containsExactly("recente", "vieille", "sans-date");
    }

    @Test
    void consulter_le_solde_d_un_tiers_est_refuse() {
        perimetre(perimetreManager());

        assertThatThrownBy(() -> service.getSolde(TIERS))
                .isInstanceOf(CongeAccesRefuseException.class);
        // Le refus tombe AVANT le findById : on ne divulgue pas l'existence du dossier.
        org.mockito.Mockito.verify(dossierEmployeRepository, org.mockito.Mockito.never()).findById(TIERS);
    }

    @Test
    void consulter_le_solde_d_un_subordonne_est_autorise() {
        perimetre(perimetreManager());
        when(dossierEmployeRepository.findById(SUBORDONNE)).thenReturn(java.util.Optional.of(
                DossierEmploye.builder().id(SUBORDONNE).matricule("M-2").nom("Diop").build()));
        when(demandeCongeRepository.findByEmployeIdAndDateDebutBetween(eq(SUBORDONNE), any(), any()))
                .thenReturn(List.of());

        assertThat(service.getSolde(SUBORDONNE).getEmployeId()).isEqualTo(SUBORDONNE);
    }

    // ─── getSoldes ────────────────────────────────────────────────────────────

    @Test
    void la_liste_des_soldes_est_limitee_au_perimetre() {
        perimetre(perimetreManager());
        when(dossierEmployeRepository.findAllById(anyIterable())).thenReturn(List.of(
                DossierEmploye.builder().id(MOI).statut(StatutDossierEmploye.ACTIF).build(),
                DossierEmploye.builder().id(SUBORDONNE).statut(StatutDossierEmploye.ACTIF).build()));
        when(demandeCongeRepository.findByEmployeIdAndDateDebutBetween(any(), any(), any()))
                .thenReturn(List.of());

        List<SoldeCongeDto> soldes = service.getSoldes();

        assertThat(soldes).extracting(SoldeCongeDto::getEmployeId)
                .containsExactlyInAnyOrder(MOI, SUBORDONNE);
        // On part du périmètre : pas de scan de tous les dossiers employés.
        org.mockito.Mockito.verify(dossierEmployeRepository, org.mockito.Mockito.never())
                .findByStatutIn(any());
    }

    @Test
    void les_employes_sortis_restent_exclus_des_soldes() {
        perimetre(perimetreManager());
        when(dossierEmployeRepository.findAllById(anyIterable())).thenReturn(List.of(
                DossierEmploye.builder().id(MOI).statut(StatutDossierEmploye.ACTIF).build(),
                DossierEmploye.builder().id(SUBORDONNE).statut(StatutDossierEmploye.SORTI).build()));
        when(demandeCongeRepository.findByEmployeIdAndDateDebutBetween(any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.getSoldes()).extracting(SoldeCongeDto::getEmployeId).containsExactly(MOI);
    }

    @Test
    void la_rh_obtient_les_soldes_de_tous_les_employes_actifs() {
        perimetre(PerimetreConges.tout());
        when(dossierEmployeRepository.findByStatutIn(any())).thenReturn(List.of(
                DossierEmploye.builder().id(MOI).statut(StatutDossierEmploye.ACTIF).build(),
                DossierEmploye.builder().id(TIERS).statut(StatutDossierEmploye.ACTIF).build()));
        when(demandeCongeRepository.findByEmployeIdAndDateDebutBetween(any(), any(), any()))
                .thenReturn(List.of());

        assertThat(service.getSoldes()).hasSize(2);
    }

    @Test
    void un_compte_non_rattache_n_obtient_aucun_solde() {
        perimetre(PerimetreConges.vide());

        assertThat(service.getSoldes()).isEmpty();
    }
}
