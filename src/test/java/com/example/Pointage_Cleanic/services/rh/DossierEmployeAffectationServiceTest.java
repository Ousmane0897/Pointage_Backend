package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.AffectationSiteDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Mapper.rh.DossierEmployeMapper;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
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

import java.time.LocalDate;
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

    @BeforeEach
    void setUp() {
        service = new DossierEmployeService(repository, mapper, mongoTemplate);

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
        when(mapper.toAffectationEntities(any())).thenAnswer(inv -> {
            List<AffectationSiteDto> dtos = inv.getArgument(0);
            if (dtos == null) return null;
            return dtos.stream()
                    .map(d -> AffectationSite.builder()
                            .site(d.getSite())
                            .horaireDebut(d.getHoraireDebut())
                            .horaireFin(d.getHoraireFin())
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
