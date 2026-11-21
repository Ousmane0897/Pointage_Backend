package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * C7 — garde-fou : <b>aucune route publique non prévue</b>.
 *
 * <p>Parcourt toutes les routes MVC réellement exposées, les appelle <b>sans jeton</b> et exige un
 * 401 pour chacune, sauf celles de la liste blanche ci-dessous. Ajouter une route publique impose
 * donc de l'écrire ici, en connaissance de cause (et dans {@code SecurityConfig}).
 *
 * <p>Sans jeton, la chaîne de sécurité répond avant le contrôleur : les appels d'écriture de ce
 * test n'ont aucun effet. Seules les routes publiques atteignent leur contrôleur.
 */
class RoutesPubliquesIT extends SecuriteITBase {

    /** « MÉTHODE motif » des routes volontairement publiques. */
    private static final Set<String> LISTE_BLANCHE = Set.of(
            "POST /api/login",                      // connexion
            "POST /auth/forgot-password",           // mot de passe oublié (réponse neutre)
            "POST /auth/reset-password",            // e-mail + code, 5 essais
            "POST /api/pointages",                  // kiosque, limité en débit
            "GET /v3/api-docs",                     // documentation (désactivée en prod)
            "GET /v3/api-docs.yaml",
            "GET /v3/api-docs/swagger-config",
            "GET /swagger-ui.html",
            "GET /error",
            // ⚠ Images publiques en attente de la preuve de non-utilisation (journaux nginx, C7) :
            // à retirer de SecurityConfig et d'ici une fois l'absence d'appels confirmée.
            "GET /api/produits/image/{codeProduit}",
            "GET /api/employe-complet/image/{agentId}"
    );

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void seules_les_routes_de_la_liste_blanche_sont_publiques() throws Exception {
        Set<String> routes = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methodes = info.getMethodsCondition().getMethods();
            for (String motif : info.getPatternValues()) {
                if (methodes.isEmpty()) {
                    routes.add("GET " + motif);
                } else {
                    methodes.forEach(m -> routes.add(m.name() + " " + motif));
                }
            }
        }
        assertThat(routes).as("routes découvertes").hasSizeGreaterThan(300);

        List<String> publiquesNonPrevues = new ArrayList<>();
        for (String route : routes) {
            if (LISTE_BLANCHE.contains(route)) {
                continue;
            }
            String[] parties = route.split(" ", 2);
            String url = parties[1].replaceAll("\\{[^}]+}", "x");
            int statut = mockMvc.perform(request(HttpMethod.valueOf(parties[0]), url)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            if (statut != 401) {
                publiquesNonPrevues.add(route + " → " + statut);
            }
        }
        assertThat(publiquesNonPrevues).as("routes accessibles sans jeton hors liste blanche").isEmpty();
    }

    /** La liste blanche ne doit pas garder de route qui n'existe plus. */
    @Test
    void la_liste_blanche_ne_contient_que_des_routes_existantes() {
        Set<String> routes = new TreeSet<>();
        for (RequestMappingInfo info : mappings.getHandlerMethods().keySet()) {
            Set<RequestMethod> methodes = info.getMethodsCondition().getMethods();
            for (String motif : info.getPatternValues()) {
                if (methodes.isEmpty()) {
                    routes.add("GET " + motif);
                } else {
                    methodes.forEach(m -> routes.add(m.name() + " " + motif));
                }
            }
        }
        assertThat(routes).containsAll(LISTE_BLANCHE);
    }
}
