package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.Dto.rh.AffectationSiteDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.services.rh.DossierEmployeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT du back-fill des affectations + round-trips CRUD réels (mapper + Mongo)
 * pour le champ affectations / dérivation siteAffecte.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class AffectationSiteBackfillRunnerIT extends MongoTestContainer {

    private static final String COLLECTION = "dossiers_employes";

    @Autowired
    private AffectationSiteBackfillRunner runner;

    @Autowired
    private DossierEmployeService service;

    @Autowired
    private DossierEmployeRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    void clean() {
        mongoTemplate.getCollection(COLLECTION).drop();
    }

    @Test
    void create_puis_get_conserve_affectations_et_derive_siteAffecte() throws Exception {
        DossierEmployeDto dto = baseDto("MAT-1", "1001");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Sacré-Coeur").horaireDebut("06:00").horaireFin("12:00").build(),
                AffectationSiteDto.builder().site("Point E").build()));

        DossierEmployeDto cree = service.create(dto, null);

        DossierEmployeDto recharge = service.getById(cree.getId());
        assertThat(recharge.getSiteAffecte()).isEqualTo("Sacré-Coeur - Point E");
        assertThat(recharge.getAffectations()).hasSize(2);
        assertThat(recharge.getAffectations().get(0).getSite()).isEqualTo("Sacré-Coeur");
        assertThat(recharge.getAffectations().get(0).getHoraireDebut()).isEqualTo("06:00");
        assertThat(recharge.getAffectations().get(0).getHoraireFin()).isEqualTo("12:00");
        assertThat(recharge.getAffectations().get(1).getSite()).isEqualTo("Point E");
        assertThat(recharge.getAffectations().get(1).getHoraireDebut()).isNull();
    }

    @Test
    void update_remplace_la_liste() throws Exception {
        DossierEmployeDto dto = baseDto("MAT-2", "1002");
        dto.setAffectations(List.of(
                AffectationSiteDto.builder().site("Ancien A").build(),
                AffectationSiteDto.builder().site("Ancien B").build()));
        DossierEmployeDto cree = service.create(dto, null);

        DossierEmployeDto maj = baseDto("MAT-2", "1002");
        maj.setAffectations(List.of(
                AffectationSiteDto.builder().site("Nouveau").horaireDebut("08:00").horaireFin("17:00").build()));
        service.update(cree.getId(), maj, null);

        DossierEmployeDto recharge = service.getById(cree.getId());
        assertThat(recharge.getAffectations()).hasSize(1);
        assertThat(recharge.getAffectations().get(0).getSite()).isEqualTo("Nouveau");
        assertThat(recharge.getSiteAffecte()).isEqualTo("Nouveau");
    }

    @Test
    void create_retrocompat_siteAffecte_seul() throws Exception {
        DossierEmployeDto dto = baseDto("MAT-3", "1003");
        dto.setSiteAffecte("Sacré-Coeur / Point E");
        dto.setAffectations(null);

        DossierEmployeDto cree = service.create(dto, null);

        DossierEmployeDto recharge = service.getById(cree.getId());
        assertThat(recharge.getSiteAffecte()).isEqualTo("Sacré-Coeur / Point E");
        assertThat(recharge.getAffectations()).hasSize(2);
        assertThat(recharge.getAffectations()).extracting(AffectationSiteDto::getSite)
                .containsExactly("Sacré-Coeur", "Point E");
        assertThat(recharge.getAffectations().get(0).getHoraireDebut()).isNull();
    }

    @Test
    void backfill_remplit_affectations_depuis_siteAffecte_et_preserve_tiret_colle() {
        // Dossier historique : siteAffecte renseigné, affectations absentes.
        DossierEmploye legacy = DossierEmploye.builder()
                .matricule("MAT-LEG").agentId("1900").nom("Leg").prenom("Acy")
                .dateEmbauche(LocalDate.of(2025, 1, 1)).statut(StatutDossierEmploye.ACTIF)
                .siteAffecte("Sacré-Coeur / Point E")
                .build();
        repository.save(legacy);

        runner.run();

        DossierEmploye migre = repository.findByMatricule("MAT-LEG").orElseThrow();
        assertThat(migre.getAffectations()).hasSize(2);
        assertThat(migre.getAffectations()).extracting(AffectationSite::getSite)
                .containsExactly("Sacré-Coeur", "Point E");
        assertThat(migre.getAffectations().get(0).getHoraireDebut()).isNull();
    }

    @Test
    void backfill_ne_reconstruit_pas_la_liste_des_dossiers_deja_pourvus() {
        DossierEmploye dejaPourvu = DossierEmploye.builder()
                .matricule("MAT-OK").agentId("1901").nom("Ok").prenom("Deja")
                .dateEmbauche(LocalDate.of(2025, 1, 1)).statut(StatutDossierEmploye.ACTIF)
                .siteAffecte("Site X - Site Y")
                .affectations(List.of(
                        AffectationSite.builder().site("Site X").horaireDebut("07:00").horaireFin("15:00").build()))
                .build();
        repository.save(dejaPourvu);

        runner.run();
        runner.run();

        DossierEmploye inchange = repository.findByMatricule("MAT-OK").orElseThrow();
        // La liste d'origine (1 seul site, avec horaires) est conservée telle quelle : la
        // passe 1 ne la reconstruit pas depuis siteAffecte, qui en annonce pourtant deux.
        assertThat(inchange.getAffectations()).hasSize(1);
        assertThat(inchange.getAffectations().get(0).getHoraireDebut()).isEqualTo("07:00");
    }

    // ---- Période et semaine ouvrée par site -------------------------------------------

    @Test
    void round_trip_des_champs_periode_et_jours_par_site() throws Exception {
        DossierEmployeDto dto = baseDto("MAT-RT", "1010");
        dto.setAffectations(List.of(AffectationSiteDto.builder()
                .site("Yoff").horaireDebut("06:00").horaireFin("12:00")
                .dateEntree(LocalDate.of(2026, 3, 1))
                .dateSortie(LocalDate.of(2026, 12, 31))
                .joursTravail("LUN_SAM")
                .build()));

        DossierEmployeDto recharge = service.getById(service.create(dto, null).getId());

        AffectationSiteDto affectation = recharge.getAffectations().get(0);
        assertThat(affectation.getDateEntree()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(affectation.getDateSortie()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(affectation.getJoursTravail()).isEqualTo("LUN_SAM");
    }

    @Test
    void backfill_propage_le_rythme_de_l_employe_et_la_date_d_embauche_sur_chaque_site() {
        DossierEmploye legacy = DossierEmploye.builder()
                .matricule("MAT-PROP").agentId("1911").nom("Prop").prenom("Aga")
                .dateEmbauche(LocalDate.of(2025, 4, 15)).statut(StatutDossierEmploye.ACTIF)
                .joursTravail("LUN_SAM")
                .siteAffecte("Site A - Site B")
                .affectations(List.of(
                        AffectationSite.builder().site("Site A").build(),
                        AffectationSite.builder().site("Site B").build()))
                .build();
        repository.save(legacy);

        runner.run();
        runner.run();   // idempotent

        DossierEmploye migre = repository.findByMatricule("MAT-PROP").orElseThrow();
        assertThat(migre.getAffectations()).allSatisfy(a -> {
            assertThat(a.getJoursTravail()).isEqualTo("LUN_SAM");
            assertThat(a.getDateEntree()).isEqualTo(LocalDate.of(2025, 4, 15));
            // « Toujours en poste » est le bon défaut : inventer une sortie ferait
            // disparaître les lignes de pointage du site.
            assertThat(a.getDateSortie()).isNull();
        });
    }

    @Test
    void backfill_n_ecrase_jamais_une_valeur_saisie() {
        DossierEmploye saisi = DossierEmploye.builder()
                .matricule("MAT-KEEP").agentId("1912").nom("Keep").prenom("Val")
                .dateEmbauche(LocalDate.of(2025, 1, 1)).statut(StatutDossierEmploye.ACTIF)
                .joursTravail("LUN_VEN")
                .siteAffecte("Site Z")
                .affectations(List.of(AffectationSite.builder()
                        .site("Site Z").joursTravail("LUN_DIM")
                        .dateEntree(LocalDate.of(2026, 6, 1)).build()))
                .build();
        repository.save(saisi);

        runner.run();

        AffectationSite affectation =
                repository.findByMatricule("MAT-KEEP").orElseThrow().getAffectations().get(0);
        assertThat(affectation.getJoursTravail()).isEqualTo("LUN_DIM");
        assertThat(affectation.getDateEntree()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    // ---- Identité de ligne -------------------------------------------------------------

    /**
     * La liste d'affectations étant remplacée en bloc à chaque écriture, aucune ligne
     * antérieure ne recevrait jamais d'identifiant sans cette passe.
     */
    @Test
    void backfill_pose_un_id_sur_les_affectations_qui_n_en_ont_pas() {
        DossierEmploye sansIds = DossierEmploye.builder()
                .matricule("MAT-ID").agentId("1914").nom("Id").prenom("Sans")
                .dateEmbauche(LocalDate.of(2025, 1, 1)).statut(StatutDossierEmploye.ACTIF)
                .siteAffecte("Site A - Site B")
                .affectations(List.of(
                        AffectationSite.builder().site("Site A").build(),
                        AffectationSite.builder().site("Site B").build()))
                .build();
        repository.save(sansIds);

        runner.run();

        assertThat(repository.findByMatricule("MAT-ID").orElseThrow().getAffectations())
                .extracting(AffectationSite::getId)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
    }

    @Test
    void backfill_ne_regenere_pas_les_ids_au_second_demarrage() {
        DossierEmploye dossier = DossierEmploye.builder()
                .matricule("MAT-IDEM").agentId("1915").nom("Idem").prenom("Pot")
                .dateEmbauche(LocalDate.of(2025, 1, 1)).statut(StatutDossierEmploye.ACTIF)
                .siteAffecte("Site A")
                .affectations(List.of(AffectationSite.builder().site("Site A").build()))
                .build();
        repository.save(dossier);

        runner.run();
        String pose = repository.findByMatricule("MAT-IDEM").orElseThrow()
                .getAffectations().get(0).getId();

        runner.run();

        assertThat(repository.findByMatricule("MAT-IDEM").orElseThrow()
                .getAffectations().get(0).getId()).isEqualTo(pose);
    }

    /** Les affectations dérivées de {@code siteAffecte} par la passe 1 en reçoivent un aussi. */
    @Test
    void backfill_pose_un_id_sur_les_affectations_qu_il_vient_de_deriver() {
        DossierEmploye legacy = DossierEmploye.builder()
                .matricule("MAT-DERIVE").agentId("1916").nom("Derive").prenom("Aga")
                .dateEmbauche(LocalDate.of(2025, 1, 1)).statut(StatutDossierEmploye.ACTIF)
                .siteAffecte("Sacré-Coeur / Point E")
                .build();
        repository.save(legacy);

        runner.run();

        assertThat(repository.findByMatricule("MAT-DERIVE").orElseThrow().getAffectations())
                .hasSize(2)
                .extracting(AffectationSite::getId).doesNotContainNull();
    }

    /**
     * {@code dateEmbauche} est mappé sur le champ Mongo historique {@code dateEntree} :
     * un dossier écrit avant le renommage doit continuer à rendre sa date. Sans le
     * {@code @Field}, la date d'embauche de tout le parc serait devenue nulle.
     */
    @Test
    void dateEmbauche_relit_le_champ_mongo_historique_dateEntree() {
        mongoTemplate.getCollection(COLLECTION).insertOne(new org.bson.Document()
                .append("matricule", "MAT-OLD")
                .append("agentId", "1913")
                .append("nom", "Old").append("prenom", "Doc")
                .append("statut", StatutDossierEmploye.ACTIF.name())
                .append("dateEntree", "2024-02-29"));

        DossierEmploye relu = repository.findByMatricule("MAT-OLD").orElseThrow();

        assertThat(relu.getDateEmbauche()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    private DossierEmployeDto baseDto(String matricule, String agentId) {
        return DossierEmployeDto.builder()
                .matricule(matricule).agentId(agentId).nom("X").prenom("Y").poste("Op")
                .dateEmbauche(LocalDate.of(2026, 1, 1))
                .statut(StatutDossierEmploye.ACTIF)
                .build();
    }
}
