package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Critère 1b « RoutesSuppressionIT » (décision D4) : <b>aucune route DELETE hors liste blanche</b>.
 * Une pièce s'annule ou s'abandonne, un référentiel se désactive : rien ne se supprime.
 *
 * <p>Liste blanche en deux parties, comme {@code SuppressionsInterditesArchTest} :
 * <ul>
 *   <li>{@link #PERMANENTES} : suppressions légitimes (aucune route aujourd'hui) ;</li>
 *   <li>{@link #EN_SURSIS} : routes existantes, chacune avec la sous-branche qui la retirera.
 *       La liste ne peut que diminuer : une nouvelle route DELETE fait échouer ce test.</li>
 * </ul>
 * Les routes retirées en 1b (bons et inventaires de stock) sont vérifiées absentes.
 */
class RoutesSuppressionIT extends SecuriteITBase {

    private static final Set<String> PERMANENTES = Set.of();

    /** Route → sous-branche qui la retirera (désactivation, annulation tracée ou pièce jointe). */
    static final Map<String, String> EN_SURSIS = Map.ofEntries(
            // 1d — référentiels employé et site
            entry("DELETE /api/employe-complet/by-agent/{agentId}", "1d"),
            entry("DELETE /api/gestion-personnel/employes/{id}", "1d"),
            entry("DELETE /api/terrain/sites-clients/{id}", "1d"),
            // 1e — référentiel article unique
            entry("DELETE /api/stock/produits/{id}", "1e"),
            entry("DELETE /api/stock/categories/{id}", "1e"),
            entry("DELETE /api/production-chimie/stock-chimie/matieres-premieres/{id}", "1e"),
            entry("DELETE /api/production-chimie/formats-conditionnement/{id}", "1e"),
            entry("DELETE /api/terrain/phytosanitaire/produits/{id}", "1e"),
            entry("DELETE /api/terrain/materiel/{id}", "1e"),
            // 1f — grille des salaires
            entry("DELETE /api/paie/grille-salariale/{id}", "1f"),
            // 1g — comptes
            entry("DELETE /api/superadmin/{id}", "1g"),
            // 1c — paramètres datés
            entry("DELETE /api/temps-presences/jours-feries/{id}", "1c"),
            // 1i — fichiers et pièces jointes
            entry("DELETE /api/employe-complet/{id}/contrat", "1i"),
            entry("DELETE /api/gestion-personnel/contrats/{id}/fichier", "1i"),
            entry("DELETE /api/production-chimie/stock-chimie/matieres-premieres/{id}/fiche-securite", "1i"),
            // Pièces et paramètres des modules métier, portés sur le modèle pièce dans le lot de
            // leur module (lot-1-conception.md §4, sous 1b) : RH lot 6, Terrain lot 2, Stock lot 3,
            // Production chimie lot 8
            entry("DELETE /api/developpement-rh/evaluations/{id}", "lot 6"),
            entry("DELETE /api/developpement-rh/formations/besoins/{id}", "lot 6"),
            entry("DELETE /api/developpement-rh/formations/sessions/{sessionId}", "lot 6"),
            entry("DELETE /api/developpement-rh/formations/{id}", "lot 6"),
            entry("DELETE /api/developpement-rh/sanctions/{id}", "lot 6"),
            entry("DELETE /api/gestion-personnel/contrats/{id}", "lot 6"),
            entry("DELETE /api/gestion-personnel/documents/{id}", "lot 6"),
            entry("DELETE /api/paie/bulletins/{id}", "lot 6"),
            entry("DELETE /api/paie/declarations-sociales/{id}", "lot 6"),
            entry("DELETE /api/temps-presences/absences/{id}", "lot 6"),
            entry("DELETE /api/temps-presences/conges/demandes/{id}", "lot 6"),
            entry("DELETE /api/temps-presences/heures-supplementaires/{id}", "lot 6"),
            entry("DELETE /api/terrain/controles-terrain/grilles/{id}", "lot 2"),
            entry("DELETE /api/terrain/interventions/{id}", "lot 2"),
            entry("DELETE /api/terrain/phytosanitaire/applications/{id}", "lot 2"),
            entry("DELETE /api/production-chimie/controle-qualite/grilles/{id}", "lot 8"),
            entry("DELETE /api/production-chimie/formulations/{id}", "lot 8"),
            entry("DELETE /api/stock/chantiers/{id}", "lot 3"),
            entry("DELETE /api/stock/plafonds/{id}", "lot 3")
    );

    /** Retirées en 1b : elles ne doivent pas réapparaître. */
    private static final Set<String> RETIREES_EN_1B = Set.of(
            "DELETE /api/stock/bons-entree/{id}",
            "DELETE /api/stock/bons-sortie/{id}",
            "DELETE /api/stock/inventaires/{id}",
            "POST /api/stock/bons-entree/{id}/suppression-definitive",
            "POST /api/stock/bons-sortie/{id}/suppression-definitive",
            "POST /api/stock/inventaires/{id}/suppression-definitive"
    );

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void aucune_route_delete_hors_liste_blanche() {
        Set<String> routes = routes();
        assertThat(routes).as("routes découvertes").hasSizeGreaterThan(300);

        Set<String> delete = new TreeSet<>();
        routes.stream().filter(r -> r.startsWith("DELETE ")).forEach(delete::add);

        Set<String> nonPrevues = new TreeSet<>(delete);
        nonPrevues.removeAll(PERMANENTES);
        nonPrevues.removeAll(EN_SURSIS.keySet());
        assertThat(nonPrevues).as("routes DELETE hors liste blanche : annuler, abandonner ou désactiver").isEmpty();

        // La liste en sursis ne garde pas de route disparue : elle doit diminuer avec les sous-branches.
        Set<String> obsoletes = new TreeSet<>(EN_SURSIS.keySet());
        obsoletes.removeAll(delete);
        assertThat(obsoletes).as("routes en sursis déjà retirées : les enlever de la liste").isEmpty();

        assertThat(routes).doesNotContainAnyElementsOf(RETIREES_EN_1B);
    }

    private Set<String> routes() {
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
        return routes;
    }
}
