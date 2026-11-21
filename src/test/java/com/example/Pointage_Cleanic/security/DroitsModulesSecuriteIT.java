package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * C4 — une route représentative par contrôleur (hors paie et dossiers, testés à part) :
 * 401 sans jeton, 403 sans droit et avec un drapeau voisin, accès avec le bon drapeau.
 */
class DroitsModulesSecuriteIT extends SecuriteITBase {

    static Stream<Arguments> routes() {
        return Stream.of(
                // --- RH (reste) ---
                a("GET", "/api/gestion-personnel/documents", "{\"rh\":{\"documents\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/gestion-personnel/organigramme", "{\"rh\":{\"organigramme\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/temps-presences/pointages", "{\"rh\":{\"pointageCentralise\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/temps-presences/recapitulatif?mois=1&annee=2026", "{\"rh\":{\"recapitulatif\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/temps-presences/heures-supplementaires", "{\"rh\":{\"heuresSupplementaires\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/temps-presences/absences", "{\"rh\":{\"absences\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/developpement-rh/formations", "{\"rh\":{\"formations\":true}}", "{\"rh\":{\"sanctions\":true}}"),
                a("GET", "/api/developpement-rh/formations/besoins", "{\"rh\":{\"formations\":true}}", "{\"rh\":{\"sanctions\":true}}"),
                a("GET", "/api/developpement-rh/evaluations", "{\"rh\":{\"evaluations\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/developpement-rh/sanctions", "{\"rh\":{\"sanctions\":true}}", "{\"rh\":{\"formations\":true}}"),
                a("GET", "/api/developpement-rh/tableau-bord", "{\"rh\":{\"tableauBordRh\":true}}", "{\"rh\":{\"formations\":true}}"),
                // --- Stock v2 ---,
                a("GET", "/api/stock/produits", "{\"stock\":{\"catalogue\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("POST", "/api/stock/categories", "{\"stock\":{\"catalogue\":true}}", "{\"stock\":{\"bonsSortie\":true}}"),
                a("PATCH", "/api/stock/produits/inexistant/prix-vente", "{\"stock\":{\"coutUnitaire\":true}}", "{\"stock\":{\"catalogue\":true}}"),
                a("PUT", "/api/stock/etat-stock/seuils", "{\"stock\":{\"etatStock\":true}}", "{\"stock\":{\"bonsSortie\":true}}"),
                a("GET", "/api/stock/mouvements", "{\"stock\":{\"mouvements\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("POST", "/api/stock/chantiers", "{\"stock\":{\"chantiers\":true}}", "{\"stock\":{\"dons\":true}}"),
                a("GET", "/api/stock/inventaires", "{\"stock\":{\"inventaires\":true}}", "{\"stock\":{\"synthese\":true}}"),
                a("GET", "/api/stock/synthese-mensuelle?mois=2026-01", "{\"stock\":{\"synthese\":true}}", "{\"stock\":{\"inventaires\":true}}"),
                a("GET", "/api/stock/approvisionnement/suggestions", "{\"stock\":{\"approvisionnement\":true}}", "{\"stock\":{\"synthese\":true}}"),
                a("GET", "/api/stock/tableau-bord", "{\"stock\":{\"tableauBord\":true}}", "{\"stock\":{\"synthese\":true}}"),
                a("GET", "/api/stock/bons-entree", "{\"stock\":{\"bonsEntree\":true}}", "{\"stock\":{\"bonsSortie\":true}}"),
                a("GET", "/api/stock/bons-sortie", "{\"stock\":{\"bonsSortie\":true}}", "{\"stock\":{\"bonsEntree\":true}}"),
                a("GET", "/api/stock/workflow/bons", "{\"stock\":{\"workflowValidation\":true}}", "{\"stock\":{\"bonsEntree\":true}}"),
                a("GET", "/api/stock/categorisation/stats?sens=ENTREE", "{\"stock\":{\"categorisation\":true}}", "{\"stock\":{\"dotation\":true}}"),
                a("GET", "/api/stock/plafonds", "{\"stock\":{\"plafonds\":true}}", "{\"stock\":{\"dons\":true}}"),
                a("POST", "/api/stock/plafonds", "{\"stock\":{\"plafonds\":true}}", "{\"stock\":{\"bonsSortie\":true}}"),
                a("GET", "/api/stock/dotation/comparatif?mois=2026-01", "{\"stock\":{\"dotation\":true}}", "{\"stock\":{\"plafonds\":true}}"),
                a("GET", "/api/stock/consommation/par-destinataire", "{\"stock\":{\"historiqueDestinataire\":true}}", "{\"stock\":{\"rapportsConso\":true}}"),
                a("GET", "/api/stock/consommation/rapport?type=PAR_SITE&dateDebut=2026-01-01&dateFin=2026-01-31", "{\"stock\":{\"rapportsConso\":true}}", "{\"stock\":{\"historiqueDestinataire\":true}}"),
                a("GET", "/api/stock/analyse/mensuel?mois=2026-01", "{\"stock\":{\"analyseMensuelle\":true}}", "{\"stock\":{\"dons\":true}}"),
                a("GET", "/api/stock/analyse/dons?dateDebut=2026-01-01&dateFin=2026-01-31", "{\"stock\":{\"dons\":true}}", "{\"stock\":{\"analyseMensuelle\":true}}"),
                a("GET", "/api/stock/analyse/comparatif?axe=SITE&dateDebut=2026-01&dateFin=2026-03&seuilPct=20", "{\"stock\":{\"comparatif\":true}}", "{\"stock\":{\"dons\":true}}"),
                a("GET", "/api/stock/analyse/croise?axeLignes=PRODUIT&mesure=MONTANT&dateDebut=2026-01-01&dateFin=2026-01-31", "{\"stock\":{\"filtresCroises\":true}}", "{\"stock\":{\"comparatif\":true}}"),
                a("GET", "/api/stock/valorisation/parametrage", "{\"stock\":{\"valeurStock\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("PUT", "/api/stock/valorisation/parametrage", "{\"stock\":{\"coutUnitaire\":true}}", "{\"stock\":{\"valeurStock\":true}}"),
                a("GET", "/api/stock/valorisation/couts-produits", "{\"stock\":{\"coutUnitaire\":true}}", "{\"stock\":{\"marges\":true}}"),
                a("GET", "/api/stock/valorisation/mouvements", "{\"stock\":{\"coutMouvements\":true}}", "{\"stock\":{\"marges\":true}}"),
                a("GET", "/api/stock/valorisation/valeur-stock", "{\"stock\":{\"valeurStock\":true}}", "{\"stock\":{\"marges\":true}}"),
                a("GET", "/api/stock/valorisation/cout-site?dateDebut=2026-01-01&dateFin=2026-01-31", "{\"stock\":{\"coutSite\":true}}", "{\"stock\":{\"marges\":true}}"),
                a("GET", "/api/stock/valorisation/chantiers", "{\"stock\":{\"coutChantier\":true}}", "{\"stock\":{\"marges\":true}}"),
                a("GET", "/api/stock/valorisation/marges?dateDebut=2026-01-01&dateFin=2026-01-31", "{\"stock\":{\"marges\":true}}", "{\"stock\":{\"coutSite\":true}}"),
                a("GET", "/api/stock/valorisation/tableau-bord?dateDebut=2026-01-01&dateFin=2026-01-31", "{\"stock\":{\"tableauBordFinancier\":true}}", "{\"stock\":{\"marges\":true}}"),
                // --- Terrain ---,
                a("GET", "/api/terrain/tableau-bord/kpis?dateDebut=2026-01-01&dateFin=2026-01-31", "{\"terrain\":{\"tableauBord\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("GET", "/api/terrain/sites-clients", "{\"terrain\":{\"planning\":true}}", null),
                a("DELETE", "/api/terrain/sites-clients/inexistant", "{\"terrain\":{\"sitesClients\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("GET", "/api/terrain/planning/affectations", "{\"terrain\":{\"alertes\":true}}", null),
                a("POST", "/api/terrain/planning/affectations/inexistant/annuler", "{\"terrain\":{\"planning\":true}}", "{\"terrain\":{\"alertes\":true}}"),
                a("GET", "/api/terrain/pointages/historique", "{\"terrain\":{\"pointage\":true}}", "{\"stock\":{\"catalogue\":true}}"),
                a("GET", "/api/terrain/alertes", "{\"terrain\":{\"planning\":true}}", null),
                a("GET", "/api/terrain/alertes/parametres", "{\"terrain\":{\"alertes\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("PUT", "/api/terrain/alertes/parametres", "{\"terrain\":{\"alertes\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("DELETE", "/api/terrain/interventions/inexistant", "{\"terrain\":{\"interventions\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("DELETE", "/api/terrain/controles-terrain/grilles/inexistant", "{\"terrain\":{\"controleQualite\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("POST", "/api/terrain/materiel", "{\"terrain\":{\"materiel\":true}}", "{\"terrain\":{\"planning\":true}}"),
                a("DELETE", "/api/terrain/phytosanitaire/produits/inexistant", "{\"terrain\":{\"phytosanitaire\":true}}", "{\"terrain\":{\"planning\":true}}")
        );
    }

    private static Arguments a(String methode, String url, String autorise, String refuse) {
        return Arguments.of(methode, url, autorise, refuse);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("routes")
    void droit_exige(String methode, String url, String autorise, String refuse) throws Exception {
        java.util.List<String> refuses = new java.util.ArrayList<>();
        if (refuse != null) {
            refuses.add(refuse);
        }
        // Lectures consommées par le tableau de bord ou le référentiel multi-modules : le drapeau
        // « dashboard » y est volontairement admis.
        if (!LECTURES_TABLEAU_DE_BORD.contains(methode + " " + url)) {
            refuses.add("{\"dashboard\":true}");
        }
        verifierAcces(() -> request(HttpMethod.valueOf(methode), url)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"),
                autorise, refuses.toArray(String[]::new));
    }

    private static final java.util.Set<String> LECTURES_TABLEAU_DE_BORD = java.util.Set.of(
            "GET /api/terrain/sites-clients",
            "GET /api/terrain/planning/affectations",
            "GET /api/terrain/alertes",
            "GET /api/pointages/today",
            "GET /api/pointages/historique/search",
            "GET /api/pointages");

    /**
     * Congés et jours fériés : libre-service ouvert à tout compte connecté (« Mes demandes »,
     * solde, consultation des fériés) ; le périmètre est filtré par CongeIdentiteService.
     */
    @org.junit.jupiter.api.Test
    void conges_et_feries_restent_en_libre_service() throws Exception {
        String jeton = jetonSansDroit();
        for (String url : new String[]{"/api/temps-presences/conges/moi",
                "/api/temps-presences/conges/demandes/mes-demandes", "/api/temps-presences/jours-feries"}) {
            int statut = mockMvc.perform(avec(request(HttpMethod.GET, url), jeton)).andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(statut).as(url).isNotIn(401, 403);
        }
    }
}
