package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

/**
 * Base des tests de sécurité <b>avec la chaîne de filtres réelle</b> ({@code SecurityConfig} +
 * {@code JwtRequestFilter}), contrairement aux slices {@code @WebMvcTest(addFilters = false)}.
 *
 * <p>Les comptes sont créés en base et les jetons sont de vrais JWT signés par {@link JwtUtil} :
 * le filtre recharge l'utilisateur ({@code MyUserDetailsService}) et {@code Droits} relit ses
 * {@code modulesAutorises} en base, exactement comme en production.
 *
 * <p>Codes attendus : sans jeton → <b>401</b> (entry point global) ; jeton valide sans le droit →
 * <b>403</b> ; jeton du profil autorisé → 2xx (ou une erreur métier, jamais 401/403).
 *
 * <p>Mêmes propriétés que {@code PlanningSecuriteIT} pour partager le contexte Spring en cache.
 */
@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
@AutoConfigureMockMvc
public abstract class SecuriteITBase extends MongoTestContainer {

    protected static final String SUPERADMIN = "SUPERADMIN";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected MongoTemplate mongoTemplate;
    @Autowired protected JwtUtil jwtUtil;
    @Autowired protected ObjectMapper objectMapper;

    @AfterEach
    void nettoyerContexte() {
        SecurityContextHolder.clearContext();
    }

    /** Jeton d'un compte présent seulement dans {@code login} (cas du SUPERADMIN). */
    protected String jetonLogin(String email, String role) {
        supprimerCompte(email);
        User u = new User();
        u.setEmail(email);
        u.setPassword("x");
        u.setRole(role);
        u.setMustChangePassword(false);
        mongoTemplate.save(u);
        return jwt(email, role);
    }

    protected String jetonSuperAdmin() {
        return jetonLogin("superadmin.test@cleanic.sn", SUPERADMIN);
    }

    /**
     * Jeton d'un compte {@code utilisateur} + {@code login}.
     *
     * @param modulesJson drapeaux au format JSON de {@code ModulesAutorises}, ex.
     *                    {@code {"stock":{"catalogue":true}}} ; {@code null} = aucun module
     */
    protected String jetonUtilisateur(String email, RoleAdmin role, String modulesJson, boolean actif) {
        supprimerCompte(email);
        User login = new User();
        login.setEmail(email);
        login.setPassword("x");
        login.setRole(role.name());
        login.setMustChangePassword(false);
        mongoTemplate.save(login);

        Utilisateur u = new Utilisateur();
        u.setEmail(email);
        u.setPassword("x");
        u.setPrenom("Test");
        u.setNom("Compte");
        u.setRole(role);
        u.setActive(actif);
        u.setMustChangePassword(false);
        u.setModulesAutorises(modules(modulesJson));
        mongoTemplate.save(u);
        return jwt(email, role.name());
    }

    /** Compte actif, rôle sans privilège particulier, avec les drapeaux donnés. */
    protected String jetonAvec(String modulesJson) {
        return jetonUtilisateur("drapeaux.test@cleanic.sn", RoleAdmin.SUPERVISEUR, modulesJson, true);
    }

    /** Compte actif sans aucun drapeau. */
    protected String jetonSansDroit() {
        return jetonUtilisateur("sans.droit.test@cleanic.sn", RoleAdmin.SUPERVISEUR, null, true);
    }

    protected MockHttpServletRequestBuilder avec(MockHttpServletRequestBuilder req, String jeton) {
        return req.header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton);
    }

    /**
     * Les trois cas exigés pour une route protégée par drapeau :
     * <ol>
     *   <li>sans jeton → 401 ;</li>
     *   <li>jeton d'un compte sans aucun droit, puis de chaque profil {@code refuses} → 403 ;</li>
     *   <li>jeton du profil {@code autorise} → ni 401 ni 403 (la requête atteint le contrôleur :
     *       2xx, ou une erreur métier 400/404/422 selon les données de test).</li>
     * </ol>
     *
     * @param requete  fabrique de la requête (une nouvelle instance par appel)
     * @param autorise drapeaux JSON du profil autorisé, ex. {@code {"rh":{"calculBulletin":true}}}
     * @param refuses  drapeaux JSON de profils qui doivent être refusés (autre drapeau, autre module)
     */
    protected void verifierAcces(java.util.function.Supplier<MockHttpServletRequestBuilder> requete,
                                 String autorise, String... refuses) throws Exception {
        String libelle = requete.get().buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI();
        mockMvc.perform(requete.get())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
        mockMvc.perform(avec(requete.get(), jetonSansDroit()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        for (String refuse : refuses) {
            int statut = mockMvc.perform(avec(requete.get(), jetonAvec(refuse))).andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(statut).as(libelle + " avec " + refuse).isEqualTo(403);
        }
        int statut = mockMvc.perform(avec(requete.get(), jetonAvec(autorise))).andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(statut).as(libelle + " avec " + autorise).isNotIn(401, 403);
    }

    private ModulesAutorises modules(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ModulesAutorises.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(json, e);
        }
    }

    private String jwt(String email, String role) {
        var details = new org.springframework.security.core.userdetails.User(email, "x", List.of());
        return jwtUtil.generateToken2(details, role, email, false);
    }

    private void supprimerCompte(String email) {
        Query q = new Query(Criteria.where("email").is(email));
        mongoTemplate.remove(q, User.class);
        mongoTemplate.remove(q, Utilisateur.class);
    }
}
