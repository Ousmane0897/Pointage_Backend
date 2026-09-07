package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.EffectifSiteDto;
import com.example.Pointage_Cleanic.Enum.terrain.PerimetreEffectif;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class EffectifSiteServiceIT extends MongoTestContainer {

    @Autowired private EffectifSiteService service;
    @Autowired private MongoTemplate mongoTemplate;

    private static final String SITE_ID = "site-point-e";
    private static final String NOM_SITE = "Point E";

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), SiteClient.class);
        mongoTemplate.remove(new Query(), DossierEmploye.class);
        mongoTemplate.remove(new Query(), AffectationAgent.class);
    }

    private void siteAvecPlafond(String id, String nom, Integer plafond) {
        mongoTemplate.save(SiteClient.builder()
                .id(id).code(id).nom(nom).ville("Dakar")
                .nombreMaxEmployes(plafond).actif(true).build());
    }

    private String dossierParAffectations(String id, String... sites) {
        List<AffectationSite> affectations = java.util.Arrays.stream(sites)
                .map(s -> AffectationSite.builder().site(s).build())
                .toList();
        mongoTemplate.save(DossierEmploye.builder()
                .id(id).matricule(id).affectations(affectations).build());
        return id;
    }

    /**
     * Dossier dont l'affectation sur ce site est close. Dates volontairement très
     * antérieures : l'IT tourne sur l'horloge réelle, une date passée en dur reste
     * passée quel que soit le jour d'exécution.
     */
    private void dossierAvecAffectationClose(String id, String site) {
        mongoTemplate.save(DossierEmploye.builder()
                .id(id).matricule(id)
                .affectations(List.of(AffectationSite.builder().site(site)
                        .dateEntree(LocalDate.of(2020, 1, 1))
                        .dateSortie(LocalDate.of(2020, 6, 30)).build()))
                .build());
    }

    private String dossierParSiteAffecte(String id, String siteAffecte) {
        mongoTemplate.save(DossierEmploye.builder()
                .id(id).matricule(id).siteAffecte(siteAffecte).build());
        return id;
    }

    private void affectationTerrain(String id, String siteId, StatutAffectation statut) {
        mongoTemplate.save(AffectationAgent.builder()
                .id(id).siteId(siteId).statut(statut).build());
    }

    @Test
    void rh_compte_par_nom_avec_fallback_siteAffecte_et_sans_sur_match() {
        siteAvecPlafond(SITE_ID, NOM_SITE, 10);
        dossierParAffectations("d1", NOM_SITE);                  // affectation structurée exacte
        dossierParAffectations("d2", "Sacré-Coeur", NOM_SITE);   // multi-sites
        dossierParSiteAffecte("d3", "Sacré-Coeur - Point E");    // fallback siteAffecte
        dossierParAffectations("d4", "Point");                   // ne doit PAS matcher "Point E"
        dossierParSiteAffecte("d5", "Point");                    // idem via siteAffecte

        EffectifSiteDto effectif = service.calculer(SITE_ID, PerimetreEffectif.RH, null, null);

        assertThat(effectif.nombreActuel()).isEqualTo(3);
        assertThat(effectif.nombreMax()).isEqualTo(10);
    }

    /**
     * Un agent qui a quitté le site n'occupe plus de poste. Son affectation close étant
     * désormais conservée à vie, sans cette exclusion le plafond du site finirait par
     * saturer d'agents partis et refuserait toute nouvelle affectation.
     */
    @Test
    void rh_ne_compte_pas_un_agent_qui_a_quitte_le_site() {
        siteAvecPlafond(SITE_ID, NOM_SITE, 10);
        dossierParAffectations("d1", NOM_SITE);              // toujours en poste
        dossierAvecAffectationClose("d2", NOM_SITE);         // parti

        EffectifSiteDto effectif = service.calculer(SITE_ID, PerimetreEffectif.RH, null, null);

        assertThat(effectif.nombreActuel()).isEqualTo(1);
    }

    /**
     * ⚠ Le repli {@code siteAffecte} ne doit jouer QUE pour les dossiers dépourvus
     * d'affectations structurées. Sinon l'agent parti, dont l'affectation close ne
     * matche plus, serait recompté par le repli — la correction précédente ne servirait
     * à rien.
     */
    @Test
    void rh_n_utilise_pas_le_fallback_siteAffecte_quand_des_affectations_existent() {
        siteAvecPlafond(SITE_ID, NOM_SITE, 10);
        DossierEmploye parti = DossierEmploye.builder()
                .id("d1").matricule("d1")
                .siteAffecte(NOM_SITE)   // chaîne héritée, encore renseignée
                .affectations(List.of(AffectationSite.builder().site(NOM_SITE)
                        .dateEntree(LocalDate.of(2020, 1, 1))
                        .dateSortie(LocalDate.of(2020, 6, 30)).build()))
                .build();
        mongoTemplate.save(parti);

        EffectifSiteDto effectif = service.calculer(SITE_ID, PerimetreEffectif.RH, null, null);

        assertThat(effectif.nombreActuel()).isZero();
    }

    /** Une sortie à venir laisse l'agent en poste aujourd'hui. */
    @Test
    void rh_compte_un_agent_dont_la_sortie_est_a_venir() {
        siteAvecPlafond(SITE_ID, NOM_SITE, 10);
        mongoTemplate.save(DossierEmploye.builder()
                .id("d1").matricule("d1")
                .affectations(List.of(AffectationSite.builder().site(NOM_SITE)
                        .dateEntree(LocalDate.of(2020, 1, 1))
                        .dateSortie(LocalDate.of(2999, 12, 31)).build()))
                .build());

        EffectifSiteDto effectif = service.calculer(SITE_ID, PerimetreEffectif.RH, null, null);

        assertThat(effectif.nombreActuel()).isEqualTo(1);
    }

    @Test
    void rh_exclut_le_dossier_en_edition() {
        siteAvecPlafond(SITE_ID, NOM_SITE, 10);
        dossierParAffectations("d1", NOM_SITE);
        dossierParAffectations("d2", NOM_SITE);

        EffectifSiteDto effectif = service.calculer(SITE_ID, PerimetreEffectif.RH, "d2", null);

        assertThat(effectif.nombreActuel()).isEqualTo(1);
    }

    @Test
    void terrain_ignore_annulee_et_exclut_affectation_en_edition() {
        siteAvecPlafond(SITE_ID, NOM_SITE, 5);
        affectationTerrain("a1", SITE_ID, StatutAffectation.PLANIFIEE);
        affectationTerrain("a2", SITE_ID, StatutAffectation.EN_COURS);
        affectationTerrain("a3", SITE_ID, StatutAffectation.ANNULEE);   // ignorée
        affectationTerrain("a4", "autre-site", StatutAffectation.PLANIFIEE);

        EffectifSiteDto sansExclusion = service.calculer(SITE_ID, PerimetreEffectif.TERRAIN, null, null);
        assertThat(sansExclusion.nombreActuel()).isEqualTo(2);

        EffectifSiteDto avecExclusion = service.calculer(SITE_ID, PerimetreEffectif.TERRAIN, null, "a1");
        assertThat(avecExclusion.nombreActuel()).isEqualTo(1);
    }

    @Test
    void site_sans_plafond_renvoie_nombreMax_null() {
        siteAvecPlafond(SITE_ID, NOM_SITE, null);
        dossierParAffectations("d1", NOM_SITE);

        EffectifSiteDto effectif = service.calculer(SITE_ID, PerimetreEffectif.RH, null, null);

        assertThat(effectif.nombreActuel()).isEqualTo(1);
        assertThat(effectif.nombreMax()).isNull();
    }

    @Test
    void site_introuvable_leve_resource_not_found() {
        assertThatThrownBy(() -> service.calculer("inconnu", PerimetreEffectif.RH, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
