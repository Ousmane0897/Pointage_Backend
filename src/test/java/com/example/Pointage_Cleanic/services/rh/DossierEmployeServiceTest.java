package com.example.Pointage_Cleanic.services.rh;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeStatutRequest;
import com.example.Pointage_Cleanic.Dto.rh.EnfantEmployeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Mapper.rh.DossierEmployeMapper;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.EnfantEmploye;
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
 * Tests de la cohérence statut / dureeEssaiMois de DossierEmployeService sur les
 * chemins de save (create, update, updateStatut). Le module dédié « Période
 * d'essai / Titularisation » ayant été retiré, seule la logique de statut de
 * l'employé (dont le statut {@code EN_PERIODE_ESSAI} et le champ
 * {@code dureeEssaiMois}, conservés) est couverte ici.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DossierEmployeServiceTest {

    @Mock DossierEmployeRepository repository;
    @Mock DossierEmployeMapper mapper;
    @Mock MongoTemplate mongoTemplate;

    DossierEmployeService service;

    @BeforeEach
    void setUp() {
        // Horloge figée : le service s'en sert pour trancher si une affectation est
        // close et si une date de naissance d'enfant est dans le futur ; une horloge
        // système rendrait ces tests dépendants du jour d'exécution.
        service = new DossierEmployeService(repository, mapper, mongoTemplate,
                Clock.fixed(LocalDate.of(2026, 9, 4).atStartOfDay(ZoneId.of("Africa/Dakar"))
                        .toInstant(), ZoneId.of("Africa/Dakar")));

        // Mapper mock : copie les champs essentiels du DTO vers l'entité.
        when(mapper.toEntity(any(DossierEmployeDto.class))).thenAnswer(inv -> {
            DossierEmployeDto dto = inv.getArgument(0);
            return DossierEmploye.builder()
                    .matricule(dto.getMatricule())
                    .nom(dto.getNom())
                    .prenom(dto.getPrenom())
                    .poste(dto.getPoste())
                    .dateEmbauche(dto.getDateEmbauche())
                    .statut(dto.getStatut())
                    .dureeEssaiMois(dto.getDureeEssaiMois())
                    .joursTravail(dto.getJoursTravail())
                    .nombreEnfants(dto.getNombreEnfants())
                    .build();
        });
        when(mapper.toDto(any(DossierEmploye.class))).thenAnswer(inv -> {
            DossierEmploye e = inv.getArgument(0);
            return DossierEmployeDto.builder()
                    .id(e.getId()).matricule(e.getMatricule())
                    .nom(e.getNom()).prenom(e.getPrenom())
                    .statut(e.getStatut()).dureeEssaiMois(e.getDureeEssaiMois())
                    .joursTravail(e.getJoursTravail())
                    .build();
        });

        // ⚠ Le stub doit recopier TOUS les champs : n'en oublier un rendrait verts, pour de
        // mauvaises raisons, les tests d'identité et de tri des enfants.
        when(mapper.toEnfantEntities(org.mockito.ArgumentMatchers.anyList())).thenAnswer(inv -> {
            List<EnfantEmployeDto> dtos = inv.getArgument(0);
            return new java.util.ArrayList<>(dtos.stream()
                    .map(d -> EnfantEmploye.builder()
                            .id(d.getId()).prenom(d.getPrenom()).dateNaissance(d.getDateNaissance())
                            .build())
                    .toList());
        });

        // save assigne un id par défaut.
        when(repository.save(any(DossierEmploye.class))).thenAnswer(inv -> {
            DossierEmploye d = inv.getArgument(0);
            if (d.getId() == null) d.setId("emp-" + d.getMatricule());
            return d;
        });

        when(repository.existsByMatricule(anyString())).thenReturn(false);
    }

    @Test
    void create_employe_en_periode_essai_persiste_statut_et_duree() throws Exception {
        DossierEmployeDto dto = enPeriodeEssaiDto("M1", LocalDate.of(2026, 1, 15), 3);

        service.create(dto, null);

        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutDossierEmploye.EN_PERIODE_ESSAI);
        assertThat(captor.getValue().getDureeEssaiMois()).isEqualTo(3);
    }

    @Test
    void create_employe_actif_ne_conserve_pas_de_duree_essai() throws Exception {
        DossierEmployeDto dto = DossierEmployeDto.builder()
                .matricule("M2").agentId("0002").nom("X").prenom("Y").poste("Op")
                .dateEmbauche(LocalDate.of(2026, 1, 1))
                .statut(StatutDossierEmploye.ACTIF)
                .build();

        service.create(dto, null);

        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutDossierEmploye.ACTIF);
        assertThat(captor.getValue().getDureeEssaiMois()).isNull();
    }

    @Test
    void updateStatut_vers_periode_essai_fixe_la_duree() {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y")
                .dateEmbauche(LocalDate.of(2026, 1, 15))
                .statut(StatutDossierEmploye.ACTIF)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        DossierEmployeStatutRequest req = DossierEmployeStatutRequest.builder()
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).dureeEssaiMois(6).build();
        DossierEmployeDto result = service.updateStatut("emp1", req);

        assertThat(result.getStatut()).isEqualTo(StatutDossierEmploye.EN_PERIODE_ESSAI);
        assertThat(result.getDureeEssaiMois()).isEqualTo(6);
    }

    @Test
    void updateStatut_vers_periode_essai_sans_duree_leve_400() {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y")
                .statut(StatutDossierEmploye.ACTIF)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        DossierEmployeStatutRequest req = DossierEmployeStatutRequest.builder()
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).build();

        assertThatThrownBy(() -> service.updateStatut("emp1", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dureeEssaiMois");
    }

    @Test
    void updateStatut_de_periode_essai_vers_actif_vide_la_duree() {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp1").matricule("M1").nom("X").prenom("Y")
                .dateEmbauche(LocalDate.of(2026, 1, 15))
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI).dureeEssaiMois(3)
                .build();
        when(repository.findById("emp1")).thenReturn(Optional.of(existing));

        DossierEmployeStatutRequest req = DossierEmployeStatutRequest.builder()
                .statut(StatutDossierEmploye.ACTIF).build();
        DossierEmployeDto result = service.updateStatut("emp1", req);

        assertThat(result.getStatut()).isEqualTo(StatutDossierEmploye.ACTIF);
        assertThat(result.getDureeEssaiMois()).isNull();
    }

    @Test
    void create_persiste_et_renvoie_joursTravail_valide() throws Exception {
        DossierEmployeDto dto = actifDto("M3");
        dto.setJoursTravail("LUN_SAM");

        DossierEmployeDto result = service.create(dto, null);

        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getJoursTravail()).isEqualTo("LUN_SAM");
        assertThat(result.getJoursTravail()).isEqualTo("LUN_SAM");
    }

    @Test
    void create_joursTravail_null_est_accepte_et_renvoye_null() throws Exception {
        DossierEmployeDto dto = actifDto("M4"); // joursTravail non renseigné

        DossierEmployeDto result = service.create(dto, null);

        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getJoursTravail()).isNull();
        assertThat(result.getJoursTravail()).isNull();
    }

    @Test
    void create_joursTravail_invalide_leve_400() {
        DossierEmployeDto dto = actifDto("M5");
        dto.setJoursTravail("LUN_XYZ");

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("joursTravail");
    }

    @Test
    void update_joursTravail_invalide_leve_400() {
        DossierEmploye existing = DossierEmploye.builder()
                .id("emp9").matricule("M9").nom("X").prenom("Y")
                .statut(StatutDossierEmploye.ACTIF)
                .build();
        when(repository.findById("emp9")).thenReturn(Optional.of(existing));

        DossierEmployeDto dto = DossierEmployeDto.builder()
                .joursTravail("SAMEDI").build();

        assertThatThrownBy(() -> service.update("emp9", dto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("joursTravail");
    }

    // ─── Enfants à charge ─────────────────────────────────────────────────────

    @Test
    void create_pose_un_id_sur_chaque_enfant_et_derive_le_compteur() throws Exception {
        DossierEmployeDto dto = actifDto("M10");
        dto.setNombreEnfants(99); // valeur périmée envoyée par le client
        dto.setEnfants(List.of(
                enfant("Awa", LocalDate.of(2020, 6, 1)),
                enfant("Moussa", LocalDate.of(2015, 3, 12))));

        service.create(dto, null);

        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEnfants()).hasSize(2)
                .allSatisfy(e -> assertThat(e.getId()).isNotBlank());
        // Le compteur est dérivé, jamais celui du payload.
        assertThat(captor.getValue().getNombreEnfants()).isEqualTo(2);
        // Tri par date de naissance croissante (aîné d'abord).
        assertThat(captor.getValue().getEnfants().get(0).getPrenom()).isEqualTo("Moussa");
    }

    @Test
    void un_id_d_enfant_deja_pose_est_conserve() throws Exception {
        DossierEmployeDto dto = actifDto("M11");
        EnfantEmployeDto existant = enfant("Awa", LocalDate.of(2020, 6, 1));
        existant.setId("enfant-1");
        dto.setEnfants(List.of(existant));

        service.create(dto, null);

        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEnfants().get(0).getId()).isEqualTo("enfant-1");
    }

    @Test
    void une_liste_d_enfants_absente_laisse_le_compteur_intact() throws Exception {
        // Import bulk et clients antérieurs : sans cette branche, tout écrit effacerait
        // les enfants du dossier et son seul compteur.
        DossierEmployeDto dto = actifDto("M12");
        dto.setNombreEnfants(3);

        service.create(dto, null);

        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEnfants()).isNull();
    }

    @Test
    void une_liste_vide_ne_derive_pas_le_compteur() throws Exception {
        // Retirer toutes les lignes vide la liste, mais le compteur saisi manuellement
        // reste gouverné par le client — comportement d'avant la saisie datée.
        DossierEmployeDto dto = actifDto("M13");
        dto.setNombreEnfants(3);
        dto.setEnfants(List.of());

        service.create(dto, null);

        ArgumentCaptor<DossierEmploye> captor = ArgumentCaptor.forClass(DossierEmploye.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEnfants()).isEmpty();
        assertThat(captor.getValue().getNombreEnfants()).isEqualTo(3);
    }

    @Test
    void une_date_de_naissance_dans_le_futur_leve_400() {
        DossierEmployeDto dto = actifDto("M14");
        // L'horloge est figée au 04/09/2026.
        dto.setEnfants(List.of(enfant("Awa", LocalDate.of(2027, 1, 1))));

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("futur");
    }

    @Test
    void une_date_de_naissance_absente_leve_400() {
        DossierEmployeDto dto = actifDto("M15");
        dto.setEnfants(List.of(enfant("Awa", null)));

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("date de naissance");
    }

    private EnfantEmployeDto enfant(String prenom, LocalDate naissance) {
        return EnfantEmployeDto.builder().prenom(prenom).dateNaissance(naissance).build();
    }

    private DossierEmployeDto actifDto(String matricule) {
        return DossierEmployeDto.builder()
                .matricule(matricule).agentId("0009").nom("X").prenom("Y").poste("Op")
                .dateEmbauche(LocalDate.of(2026, 1, 1))
                .statut(StatutDossierEmploye.ACTIF)
                .build();
    }

    private DossierEmployeDto enPeriodeEssaiDto(String matricule, LocalDate dateEmbauche, int dureeMois) {
        return DossierEmployeDto.builder()
                .matricule(matricule).agentId("0001").nom("X").prenom("Y").poste("Op")
                .dateEmbauche(dateEmbauche)
                .statut(StatutDossierEmploye.EN_PERIODE_ESSAI)
                .dureeEssaiMois(dureeMois)
                .build();
    }
}
