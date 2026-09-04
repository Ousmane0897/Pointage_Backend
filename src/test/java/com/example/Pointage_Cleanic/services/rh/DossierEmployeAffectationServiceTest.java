package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.AffectationSiteDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Mapper.rh.DossierEmployeMapper;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.AffectationInvalideException;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de la logique affectations multi-sites : dérivation de siteAffecte,
 * validation de cohérence horaire (400), remplacement complet au PUT, et
 * rétro-compatibilité siteAffecte-seul.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DossierEmployeAffectationServiceTest {

    @Mock DossierEmployeRepository repository;
    @Mock DossierEmployeMapper mapper;
    @Mock MongoTemplate mongoTemplate;

    DossierEmployeService service;

    /**
     * Horloge figée : « une affectation est-elle close ? » se tranche par rapport à une
     * date, et une horloge système rendrait ces tests dépendants du jour d'exécution.
     */
    private static final LocalDate AUJOURD_HUI = LocalDate.of(2026, 9, 4);
    private static final Clock CLOCK = Clock.fixed(
            AUJOURD_HUI.atStartOfDay(ZoneId.of("Africa/Dakar")).toInstant(),
            ZoneId.of("Africa/Dakar"));

    @BeforeEach
    void setUp() {
        service = new DossierEmployeService(repository, mapper, mongoTemplate, CLOCK);

        when(mapper.toEntity(any(DossierEmployeDto.class))).thenAnswer(inv -> {
            DossierEmployeDto dto = inv.getArgument(0);
            return DossierEmploye.builder()
                    .matricule(dto.getMatricule())
                    .agentId(dto.getAgentId())
                    .nom(dto.getNom())
                    .prenom(dto.getPrenom())
                    .poste(dto.getPoste())
                    .dateEmbauche(dto.getDateEmbauche())
                    .statut(dto.getStatut())
                    .siteAffecte(dto.getSiteAffecte())
                    .build();
        });
        when(mapper.toDto(any(DossierEmploye.class))).thenAnswer(inv -> {
            DossierEmploye e = inv.getArgument(0);
            return DossierEmployeDto.builder()
                    .id(e.getId()).matricule(e.getMatricule())
                    .siteAffecte(e.getSiteAffecte())
                    .build();
        });
        // Conversion réelle DTO → entité pour les affectations.
        // ⚠ Recopier TOUS les champs, comme le fait MapStruct : un stub partiel
        // (id/dates/joursTravail oubliés) rendrait verts, pour de mauvaises raisons,
        // les tests qui portent justement sur l'identité et la période des lignes.
        when(mapper.toAffectationEntities(any())).thenAnswer(inv -> {
            List<AffectationSiteDto> dtos = inv.getArgument(0);
            if (dtos == null) return null;
            return dtos.stream()
                    .map(d -> AffectationSite.builder()
                            .id(d.getId())
                            .site(d.getSite())
                            .horaireDebut(d.getHoraireDebut())
                            .horaireFin(d.getHoraireFin())
                            .dateEntree(d.getDateEntree())
                            .dateSortie(d.getDateSortie())
                            .joursTravail(d.getJoursTravail())
                            .build())
                    .toList();
        });
        when(repository.save(any(DossierEmploye.class))).thenAnswer(inv -> {
            DossierEmploye d = inv.getArgument(0);
            if (d.getId() == null) d.setId("emp-" + d.getMatricule());
            return d;
        });
        when(repository.existsByMatricule(anyString())).thenReturn(false);
        when(repository.existsByAgentId(anyString())).thenReturn(false);
    }

    @Test
    void create_derive_siteAffecte_depuis_affectations_et_ignore_celui_du_client() throws Exception {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setSiteAffecte("VALEUR-CLIENT-A-IGNORER");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Sacré-Coeur").horaireDebut("06:00").horaireFin("12:00").build(),
                AffectationSiteDto.builder().site("Point E").build()));

        service.create(dto, null);

        DossierEmploye saved = captureSaved();
        assertThat(saved.getSiteAffecte()).isEqualTo("Sacré-Coeur - Point E");
        assertThat(saved.getAffectations()).hasSize(2);
        assertThat(saved.getAffectations().get(0).getHoraireDebut()).isEqualTo("06:00");
        assertThat(saved.getAffectations().get(1).getSite()).isEqualTo("Point E");
        assertThat(saved.getAffectations().get(1).getHoraireDebut()).isNull();
    }

    @Test
    void create_rejette_horaire_incoherent_en_400() {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Sacré-Coeur").horaireDebut("12:00").horaireFin("06:00").build()));

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("horaireDebut doit être antérieur");
    }

    @Test
    void create_rejette_horaire_egal_en_400() {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Sacré-Coeur").horaireDebut("06:00").horaireFin("06:00").build()));

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejette_une_sortie_de_site_anterieure_a_l_entree() {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(AffectationSiteDto.builder().site("Yoff")
                .dateEntree(LocalDate.of(2026, 5, 10))
                .dateSortie(LocalDate.of(2026, 5, 9)).build()));

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dateSortie");
    }

    @Test
    void create_rejette_un_rythme_de_site_hors_enum() {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(AffectationSiteDto.builder().site("Yoff")
                .joursTravail("LUN_JEU").build()));

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("joursTravail");
    }

    /**
     * Les deux bornes restent facultatives : l'import bulk et le repli
     * {@code affectationsDepuisSiteAffecte} produisent des affectations sans aucune date.
     */
    @Test
    void create_accepte_une_affectation_sans_date_ni_rythme() throws Exception {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(AffectationSiteDto.builder().site("Yoff").build()));

        service.create(dto, null);

        DossierEmploye saved = captureSaved();
        assertThat(saved.getAffectations().get(0).getDateEntree()).isNull();
        assertThat(saved.getAffectations().get(0).getJoursTravail()).isNull();
    }

    @Test
    void create_accepte_une_sortie_egale_a_l_entree() throws Exception {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(AffectationSiteDto.builder().site("Yoff")
                .dateEntree(LocalDate.of(2026, 5, 10))
                .dateSortie(LocalDate.of(2026, 5, 10)).build()));

        service.create(dto, null);

        assertThat(captureSaved().getAffectations()).hasSize(1);
    }

    @Test
    void create_accepte_un_seul_horaire_present() throws Exception {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Sacré-Coeur").horaireDebut("06:00").build()));

        service.create(dto, null);

        DossierEmploye saved = captureSaved();
        assertThat(saved.getAffectations()).hasSize(1);
        assertThat(saved.getAffectations().get(0).getHoraireFin()).isNull();
    }

    @Test
    void create_rejette_format_horaire_invalide_en_400() {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Sacré-Coeur").horaireDebut("6h").horaireFin("12:00").build()));

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Horaire invalide");
    }

    @Test
    void create_rejette_site_vide_en_400() {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("  ").horaireDebut("06:00").horaireFin("12:00").build()));

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site de l'affectation est obligatoire");
    }

    @Test
    void create_retrocompat_siteAffecte_seul_est_conserve_et_derive_affectations() throws Exception {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setSiteAffecte("Sacré-Coeur / Point E");
        dto.setAffectations(null);

        service.create(dto, null);

        DossierEmploye saved = captureSaved();
        assertThat(saved.getSiteAffecte()).isEqualTo("Sacré-Coeur / Point E");
        assertThat(saved.getAffectations()).hasSize(2);
        assertThat(saved.getAffectations().get(0).getSite()).isEqualTo("Sacré-Coeur");
        assertThat(saved.getAffectations().get(0).getHoraireDebut()).isNull();
    }

    @Test
    void update_remplace_integralement_la_liste() throws Exception {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").agentId("0001").nom("X").prenom("Y").poste("Op")
                .dateEmbauche(LocalDate.of(2026, 1, 1)).statut(StatutDossierEmploye.ACTIF)
                .siteAffecte("Ancien")
                .affectations(List.of(AffectationSite.builder().site("Ancien").build()))
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Nouveau A").build(),
                AffectationSiteDto.builder().site("Nouveau B").horaireDebut("08:00").horaireFin("17:00").build()));

        service.update("emp1", dto, null);

        DossierEmploye saved = captureSaved();
        assertThat(saved.getAffectations()).hasSize(2);
        assertThat(saved.getAffectations()).extracting(AffectationSite::getSite)
                .containsExactly("Nouveau A", "Nouveau B");
        assertThat(saved.getSiteAffecte()).isEqualTo("Nouveau A - Nouveau B");
    }

    // ─── Identité de ligne ────────────────────────────────────────────────────

    @Test
    void create_pose_un_id_sur_chaque_affectation() throws Exception {
        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Yoff").build(),
                AffectationSiteDto.builder().site("Ouakam").build()));

        service.create(dto, null);

        assertThat(captureSaved().getAffectations())
                .extracting(AffectationSite::getId)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
    }

    @Test
    void update_preserve_l_id_renvoye_et_en_pose_un_sur_la_ligne_nouvelle() throws Exception {
        when(repository.findById("emp1")).thenReturn(Optional.of(existantAvec(
                AffectationSite.builder().id("aff-1").site("Yoff")
                        .dateEntree(LocalDate.of(2026, 1, 1)).build())));

        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().id("aff-1").site("Yoff")
                        .dateEntree(LocalDate.of(2026, 1, 1)).build(),
                AffectationSiteDto.builder().site("Ouakam")
                        .dateEntree(LocalDate.of(2026, 6, 1)).build()));

        service.update("emp1", dto, null);

        List<AffectationSite> saved = captureSaved().getAffectations();
        assertThat(saved.get(0).getId()).isEqualTo("aff-1");
        assertThat(saved.get(1).getId()).isNotNull().isNotEqualTo("aff-1");
    }

    // ─── Garde anti-perte des affectations closes ─────────────────────────────

    @Test
    void update_refuse_la_disparition_d_une_affectation_close() {
        when(repository.findById("emp1")).thenReturn(Optional.of(existantAvec(closeSurYoff())));

        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(AffectationSiteDto.builder().site("Ouakam")
                .dateEntree(LocalDate.of(2026, 7, 1)).build()));

        assertThatThrownBy(() -> service.update("emp1", dto, null))
                .isInstanceOf(AffectationInvalideException.class)
                .hasMessageContaining("Yoff");
    }

    /** La garde vaut sans aucun id : elle doit protéger dès avant le backfill. */
    @Test
    void update_accepte_une_affectation_close_renvoyee_sans_id() throws Exception {
        when(repository.findById("emp1")).thenReturn(Optional.of(existantAvec(closeSurYoff())));

        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(AffectationSiteDto.builder().site("Yoff")
                .dateEntree(LocalDate.of(2026, 1, 1))
                .dateSortie(LocalDate.of(2026, 6, 30)).build()));

        service.update("emp1", dto, null);

        assertThat(captureSaved().getAffectations()).hasSize(1);
    }

    /**
     * Le payload sans {@code affectations} bascule sur la branche rétro-compat, qui
     * écrase la liste : c'est une disparition, la garde doit la voir.
     */
    @Test
    void update_refuse_un_payload_sans_affectations_quand_une_ligne_est_close() {
        when(repository.findById("emp1")).thenReturn(Optional.of(existantAvec(closeSurYoff())));

        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setSiteAffecte("Ouakam");
        dto.setAffectations(null);

        assertThatThrownBy(() -> service.update("emp1", dto, null))
                .isInstanceOf(AffectationInvalideException.class);
    }

    /** Une sortie du jour même n'est pas close : l'agent y travaille encore. */
    @Test
    void update_laisse_retirer_une_affectation_dont_la_sortie_est_aujourd_hui() throws Exception {
        when(repository.findById("emp1")).thenReturn(Optional.of(existantAvec(
                AffectationSite.builder().site("Yoff")
                        .dateEntree(LocalDate.of(2026, 1, 1))
                        .dateSortie(AUJOURD_HUI).build())));

        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(AffectationSiteDto.builder().site("Ouakam").build()));

        service.update("emp1", dto, null);

        assertThat(captureSaved().getAffectations())
                .extracting(AffectationSite::getSite).containsExactly("Ouakam");
    }

    /** Réaffecter un site quitté est le cas d'usage même de l'historique. */
    @Test
    void update_accepte_un_retour_sur_un_site_deja_quitte() throws Exception {
        when(repository.findById("emp1")).thenReturn(Optional.of(existantAvec(closeSurYoff())));

        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Yoff")
                        .dateEntree(LocalDate.of(2026, 1, 1))
                        .dateSortie(LocalDate.of(2026, 6, 30)).build(),
                AffectationSiteDto.builder().site("Yoff")
                        .dateEntree(LocalDate.of(2026, 9, 1)).build()));

        service.update("emp1", dto, null);

        assertThat(captureSaved().getAffectations()).hasSize(2);
    }

    // ─── siteAffecte dérivé ───────────────────────────────────────────────────

    @Test
    void siteAffecte_ne_liste_pas_les_sites_quittes() throws Exception {
        when(repository.findById("emp1")).thenReturn(Optional.of(existantAvec(closeSurYoff())));

        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Yoff")
                        .dateEntree(LocalDate.of(2026, 1, 1))
                        .dateSortie(LocalDate.of(2026, 6, 30)).build(),
                AffectationSiteDto.builder().site("Ouakam")
                        .dateEntree(LocalDate.of(2026, 7, 1)).build()));

        service.update("emp1", dto, null);

        DossierEmploye saved = captureSaved();
        assertThat(saved.getSiteAffecte()).isEqualTo("Ouakam");
        // La ligne close reste dans le dossier : seul le champ dérivé l'ignore.
        assertThat(saved.getAffectations()).hasSize(2);
    }

    @Test
    void siteAffecte_est_vide_quand_tous_les_sites_sont_quittes() throws Exception {
        when(repository.findById("emp1")).thenReturn(Optional.of(existantAvec(closeSurYoff())));

        DossierEmployeDto dto = baseDto("M1", "0001");
        dto.setAffectations(List.of(AffectationSiteDto.builder().site("Yoff")
                .dateEntree(LocalDate.of(2026, 1, 1))
                .dateSortie(LocalDate.of(2026, 6, 30)).build()));

        service.update("emp1", dto, null);

        assertThat(captureSaved().getSiteAffecte()).isEmpty();
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    /** Affectation quittée avant {@link #AUJOURD_HUI}. */
    private AffectationSite closeSurYoff() {
        return AffectationSite.builder().site("Yoff")
                .dateEntree(LocalDate.of(2026, 1, 1))
                .dateSortie(LocalDate.of(2026, 6, 30))
                .build();
    }

    private DossierEmploye existantAvec(AffectationSite... affectations) {
        return DossierEmploye.builder()
                .id("emp1").matricule("M1").agentId("0001").nom("X").prenom("Y").poste("Op")
                .dateEmbauche(LocalDate.of(2026, 1, 1)).statut(StatutDossierEmploye.ACTIF)
                .siteAffecte("Yoff")
                .affectations(new java.util.ArrayList<>(List.of(affectations)))
                .build();
    }

    private DossierEmployeDto baseDto(String matricule, String agentId) {
        return DossierEmployeDto.builder()
                .matricule(matricule).agentId(agentId).nom("X").prenom("Y").poste("Op")
                .dateEmbauche(LocalDate.of(2026, 1, 1))
                .statut(StatutDossierEmploye.ACTIF)
                .build();
    }

    private DossierEmploye captureSaved() {
        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
