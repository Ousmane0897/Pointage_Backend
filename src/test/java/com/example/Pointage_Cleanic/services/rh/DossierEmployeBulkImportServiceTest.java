package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeBulkImportRequest;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeBulkImportResponse;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeBulkLigneDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeImportError;
import com.example.Pointage_Cleanic.Enum.rh.GenreEmploye;
import com.example.Pointage_Cleanic.Enum.rh.SituationMatrimoniale;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.Enum.StrategieErreursImport;
import com.example.Pointage_Cleanic.Mapper.rh.DossierEmployeMapper;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de l'import bulk de dossiers employés.
 * Couvre les 12 cas du brief frontend + 1 cas pour la détection de cycles
 * + 1 cas pour le défaut TOUT_OU_RIEN appliqué quand strategieErreurs est null.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DossierEmployeBulkImportServiceTest {

    @Mock DossierEmployeRepository repository;
    @Mock DossierEmployeMapper mapper;
    @Mock MongoTemplate mongoTemplate;

    DossierEmployeService service;

    @BeforeEach
    void setUp() {
        service = new DossierEmployeService(repository, mapper, mongoTemplate);
        ReflectionTestUtils.setField(service, "bulkMaxSize", 1000);

        // Mapper mock : copie les champs du DTO vers l'entité (suffisant pour valider le flux).
        when(mapper.toEntity(any(com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto.class)))
                .thenAnswer(inv -> {
                    var dto = (com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto) inv.getArgument(0);
                    return DossierEmploye.builder()
                            .matricule(dto.getMatricule())
                            .nom(dto.getNom())
                            .prenom(dto.getPrenom())
                            .poste(dto.getPoste())
                            .departement(dto.getDepartement())
                            .siteAffecte(dto.getSiteAffecte())
                            .dateEntree(dto.getDateEntree())
                            .statut(dto.getStatut())
                            .genre(dto.getGenre())
                            .situationMatrimoniale(dto.getSituationMatrimoniale())
                            .nombreEnfants(dto.getNombreEnfants())
                            .dureeEssaiMois(dto.getDureeEssaiMois())
                            .superieurHierarchiqueId(dto.getSuperieurHierarchiqueId())
                            .superieurHierarchiqueNom(dto.getSuperieurHierarchiqueNom())
                            .build();
                });

        // saveAll : assigne un id séquentiel.
        when(repository.saveAll(anyList())).thenAnswer(inv -> {
            List<DossierEmploye> list = inv.getArgument(0);
            List<DossierEmploye> result = new ArrayList<>();
            int idx = 0;
            for (DossierEmploye d : list) {
                d.setId("id-" + (++idx));
                result.add(d);
            }
            return result;
        });

        // findByMatriculeIn : aucun conflit base par défaut.
        when(repository.findByMatriculeIn(any(Collection.class))).thenReturn(Collections.emptyList());
    }

    // ====================================================================
    //  Helpers
    // ====================================================================

    // Génère un agentId 4 chiffres unique par ligne (indépendant du matricule,
    // pour ne pas créer de faux doublons agentId dans les tests de doublon matricule).
    private static final java.util.concurrent.atomic.AtomicInteger AGENT_SEQ =
            new java.util.concurrent.atomic.AtomicInteger(1000);

    private static DossierEmployeBulkLigneDto ligneValide(String matricule) {
        DossierEmployeBulkLigneDto dto = new DossierEmployeBulkLigneDto();
        dto.setMatricule(matricule);
        dto.setAgentId(String.format("%04d", AGENT_SEQ.getAndIncrement()));
        dto.setNom("NOM_" + matricule);
        dto.setPrenom("Prenom_" + matricule);
        dto.setPoste("Agent");
        dto.setDateEntree(LocalDate.of(2026, 1, 1));
        dto.setStatut(StatutDossierEmploye.ACTIF);
        dto.setGenre(GenreEmploye.HOMME);
        return dto;
    }

    private static DossierEmployeBulkImportRequest request(
            StrategieErreursImport strat, DossierEmployeBulkLigneDto... lignes) {
        return new DossierEmployeBulkImportRequest(List.of(lignes), strat);
    }

    // ====================================================================
    //  Tests
    // ====================================================================

    /** Cas 1 : 100 % valide, stratégie TOUT_OU_RIEN → 200 + toutes insérées. */
    @Test
    void import_100_valide_tout_ou_rien_insere_tout() {
        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.TOUT_OU_RIEN,
                        ligneValide("M001"), ligneValide("M002")),
                "user@cleanic.sn");

        assertThat(res.total()).isEqualTo(2);
        assertThat(res.inserted()).isEqualTo(2);
        assertThat(res.failed()).isZero();
        assertThat(res.insertedIds()).containsExactly("id-1", "id-2");
        assertThat(res.errors()).isEmpty();
        verify(repository).saveAll(anyList());
    }

    /** Cas 2 : 100 % valide, stratégie IMPORTER_LIGNES_VALIDES → 200 + toutes insérées. */
    @Test
    void import_100_valide_importer_lignes_valides_insere_tout() {
        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.IMPORTER_LIGNES_VALIDES,
                        ligneValide("M001"), ligneValide("M002")),
                "user@cleanic.sn");

        assertThat(res.inserted()).isEqualTo(2);
        assertThat(res.errors()).isEmpty();
    }

    /** Cas 3 : 1 ligne invalide, TOUT_OU_RIEN → aucune insertion + rapport d'erreur. */
    @Test
    void import_une_invalide_tout_ou_rien_nulle_insertion() {
        DossierEmployeBulkLigneDto invalide = ligneValide("M002");
        invalide.setNom(null); // CHAMP_OBLIGATOIRE

        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.TOUT_OU_RIEN,
                        ligneValide("M001"), invalide),
                "user@cleanic.sn");

        assertThat(res.inserted()).isZero();
        assertThat(res.failed()).isEqualTo(1);
        assertThat(res.errors()).hasSize(1)
                .first()
                .satisfies(e -> {
                    assertThat(e.index()).isEqualTo(1);
                    assertThat(e.matricule()).isEqualTo("M002");
                    assertThat(e.field()).isEqualTo("nom");
                    assertThat(e.code()).isEqualTo("CHAMP_OBLIGATOIRE");
                });
        verify(repository, never()).saveAll(anyList());
    }

    /** Cas 4 : 1 ligne invalide, IMPORTER_LIGNES_VALIDES → les valides sont insérées. */
    @Test
    void import_une_invalide_importer_lignes_valides_insere_les_valides() {
        DossierEmployeBulkLigneDto invalide = ligneValide("M002");
        invalide.setNom(null);

        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.IMPORTER_LIGNES_VALIDES,
                        ligneValide("M001"), invalide, ligneValide("M003")),
                "user@cleanic.sn");

        assertThat(res.total()).isEqualTo(3);
        assertThat(res.inserted()).isEqualTo(2);
        assertThat(res.failed()).isEqualTo(1);
        assertThat(res.insertedIds()).hasSize(2);
        assertThat(res.errors()).hasSize(1);
        assertThat(res.errors().get(0).index()).isEqualTo(1);
    }

    /** Cas 5 : matricule dupliqué DANS le payload → MATRICULE_DUPLIQUE_PAYLOAD sur les deux lignes. */
    @Test
    void import_doublon_payload_marque_les_deux_lignes() {
        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.IMPORTER_LIGNES_VALIDES,
                        ligneValide("M001"),
                        ligneValide("M001")),
                "user@cleanic.sn");

        assertThat(res.errors())
                .extracting(DossierEmployeImportError::code)
                .containsOnly("MATRICULE_DUPLIQUE_PAYLOAD");
        assertThat(res.errors()).extracting(DossierEmployeImportError::index)
                .containsExactlyInAnyOrder(0, 1);
        assertThat(res.inserted()).isZero();
    }

    /** Cas 6 : matricule existant EN BASE → MATRICULE_DUPLIQUE_BASE. */
    @Test
    void import_matricule_existant_en_base() {
        when(repository.findByMatriculeIn(any(Collection.class)))
                .thenReturn(List.of(
                        DossierEmploye.builder().id("existant").matricule("M001").build()));

        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.IMPORTER_LIGNES_VALIDES,
                        ligneValide("M001"), ligneValide("M002")),
                "user@cleanic.sn");

        assertThat(res.errors()).hasSize(1);
        assertThat(res.errors().get(0).code()).isEqualTo("MATRICULE_DUPLIQUE_BASE");
        assertThat(res.errors().get(0).index()).isEqualTo(0);
        assertThat(res.inserted()).isEqualTo(1); // M002 est insérée
    }

    /** Cas 7 : supérieur référencé DANS le payload → résolution en deux passes. */
    @Test
    void import_superieur_interne_au_payload_resolu_en_deux_passes() {
        DossierEmployeBulkLigneDto manager = ligneValide("MGR1");
        DossierEmployeBulkLigneDto subordonne = ligneValide("SUB1");
        subordonne.setSuperieurHierarchiqueMatricule("MGR1");

        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.TOUT_OU_RIEN, manager, subordonne),
                "user@cleanic.sn");

        assertThat(res.errors()).isEmpty();
        assertThat(res.inserted()).isEqualTo(2);
        // Pass B : une mise à jour mongoTemplate.updateFirst sur le subordonné.
        verify(mongoTemplate).updateFirst(any(), any(), any(Class.class));
    }

    /** Cas 8 : supérieur inexistant (ni payload ni base) → SUPERIEUR_INEXISTANT. */
    @Test
    void import_superieur_inexistant() {
        DossierEmployeBulkLigneDto ligne = ligneValide("M001");
        ligne.setSuperieurHierarchiqueMatricule("INTROUVABLE");

        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.IMPORTER_LIGNES_VALIDES, ligne),
                "user@cleanic.sn");

        assertThat(res.errors()).hasSize(1);
        assertThat(res.errors().get(0).code()).isEqualTo("SUPERIEUR_INEXISTANT");
        assertThat(res.errors().get(0).field()).isEqualTo("superieurHierarchiqueMatricule");
        assertThat(res.inserted()).isZero();
    }

    /** Cas 9 : batch de 1001 → IllegalArgumentException (mappée en 400 par le handler global). */
    @Test
    void import_batch_trop_grand_leve_illegal_argument() {
        ReflectionTestUtils.setField(service, "bulkMaxSize", 5);
        List<DossierEmployeBulkLigneDto> lignes = new ArrayList<>();
        for (int i = 0; i < 6; i++) lignes.add(ligneValide("M" + i));
        DossierEmployeBulkImportRequest req =
                new DossierEmployeBulkImportRequest(lignes, StrategieErreursImport.TOUT_OU_RIEN);

        assertThatThrownBy(() -> service.importBulk(req, "user@cleanic.sn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Taille du batch");
    }

    /** Cas 10 : batch vide → IllegalArgumentException. */
    @Test
    void import_batch_vide_leve_illegal_argument() {
        DossierEmployeBulkImportRequest req = new DossierEmployeBulkImportRequest(
                List.of(), StrategieErreursImport.TOUT_OU_RIEN);

        assertThatThrownBy(() -> service.importBulk(req, "user@cleanic.sn"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vide");
    }

    /** Cas 11 : situationMatrimoniale = MARIE sans nombreEnfants → VALIDATION_CONDITIONNELLE. */
    @Test
    void import_marie_sans_nombre_enfants() {
        DossierEmployeBulkLigneDto ligne = ligneValide("M001");
        ligne.setSituationMatrimoniale(SituationMatrimoniale.MARIE);
        ligne.setNombreEnfants(null);

        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.IMPORTER_LIGNES_VALIDES, ligne),
                "user@cleanic.sn");

        assertThat(res.errors())
                .anyMatch(e -> "VALIDATION_CONDITIONNELLE".equals(e.code())
                        && "nombreEnfants".equals(e.field()));
    }

    /** Cas 12 : statut = EN_PERIODE_ESSAI sans dureeEssaiMois → VALIDATION_CONDITIONNELLE. */
    @Test
    void import_essai_sans_duree() {
        DossierEmployeBulkLigneDto ligne = ligneValide("M001");
        ligne.setStatut(StatutDossierEmploye.EN_PERIODE_ESSAI);
        ligne.setDureeEssaiMois(null);

        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.IMPORTER_LIGNES_VALIDES, ligne),
                "user@cleanic.sn");

        assertThat(res.errors())
                .anyMatch(e -> "VALIDATION_CONDITIONNELLE".equals(e.code())
                        && "dureeEssaiMois".equals(e.field()));
    }

    /** Cas 14 : payload sans `strategieErreurs` → défaut TOUT_OU_RIEN appliqué par le record. */
    @Test
    void import_sans_strategie_applique_tout_ou_rien_par_defaut() {
        DossierEmployeBulkImportRequest req = new DossierEmployeBulkImportRequest(
                List.of(ligneValide("M001")), null);

        assertThat(req.strategieErreurs()).isEqualTo(StrategieErreursImport.TOUT_OU_RIEN);

        DossierEmployeBulkImportResponse res = service.importBulk(req, "user@cleanic.sn");
        assertThat(res.inserted()).isEqualTo(1);
        assertThat(res.errors()).isEmpty();
    }

    /** Cas 13 : cycle A↔B dans le payload → REFERENCE_CIRCULAIRE sur les deux lignes. */
    @Test
    void import_reference_circulaire_entre_deux_lignes() {
        DossierEmployeBulkLigneDto a = ligneValide("A001");
        a.setSuperieurHierarchiqueMatricule("B001");
        DossierEmployeBulkLigneDto b = ligneValide("B001");
        b.setSuperieurHierarchiqueMatricule("A001");

        DossierEmployeBulkImportResponse res = service.importBulk(
                request(StrategieErreursImport.IMPORTER_LIGNES_VALIDES, a, b),
                "user@cleanic.sn");

        assertThat(res.errors())
                .filteredOn(e -> "REFERENCE_CIRCULAIRE".equals(e.code()))
                .hasSize(2)
                .extracting(DossierEmployeImportError::matricule)
                .containsExactlyInAnyOrder("A001", "B001");
        assertThat(res.inserted()).isZero();
    }
}
