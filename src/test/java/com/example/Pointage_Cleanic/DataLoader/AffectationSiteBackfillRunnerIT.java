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
                .dateEntree(LocalDate.of(2025, 1, 1)).statut(StatutDossierEmploye.ACTIF)
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
    void backfill_est_idempotent_et_ne_touche_pas_les_dossiers_deja_pourvus() {
        DossierEmploye dejaPourvu = DossierEmploye.builder()
                .matricule("MAT-OK").agentId("1901").nom("Ok").prenom("Deja")
                .dateEntree(LocalDate.of(2025, 1, 1)).statut(StatutDossierEmploye.ACTIF)
                .siteAffecte("Site X - Site Y")
                .affectations(List.of(
                        AffectationSite.builder().site("Site X").horaireDebut("07:00").horaireFin("15:00").build()))
                .build();
        repository.save(dejaPourvu);

        runner.run();
        runner.run();

        DossierEmploye inchange = repository.findByMatricule("MAT-OK").orElseThrow();
        // Non touché : la liste d'origine (1 seul site, avec horaires) est conservée.
        assertThat(inchange.getAffectations()).hasSize(1);
        assertThat(inchange.getAffectations().get(0).getHoraireDebut()).isEqualTo("07:00");
    }

    private DossierEmployeDto baseDto(String matricule, String agentId) {
        return DossierEmployeDto.builder()
                .matricule(matricule).agentId(agentId).nom("X").prenom("Y").poste("Op")
                .dateEntree(LocalDate.of(2026, 1, 1))
                .statut(StatutDossierEmploye.ACTIF)
                .build();
    }
}
