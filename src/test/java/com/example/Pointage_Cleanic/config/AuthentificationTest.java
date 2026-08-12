package com.example.Pointage_Cleanic.config;

import com.example.Pointage_Cleanic.entities.User;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * Ouvre une session applicative dans un test d'intégration.
 *
 * <p>Nécessaire dès qu'un service vérifie une habilitation : {@code CurrentUserProvider} résout le
 * rôle depuis le {@code SecurityContext} <b>puis</b> la base, un contexte absent valant « aucun
 * rôle » — donc un 403 sur les actions gouvernées par le rôle.
 *
 * <p>⚠ Le rôle {@code SUPERADMIN} n'existe que dans la collection {@code login} : il faut y créer un
 * document, poser l'authentification ne suffit pas.
 */
public final class AuthentificationTest {

    public static final String SUPERADMIN = "SUPERADMIN";
    public static final String CONTROLEUR_STOCK = "CONTROLEUR_STOCK";

    /** Compte par défaut des tests qui ont seulement besoin d'être habilités. */
    public static final String EMAIL_SUPERADMIN = "boss@cleanic.sn";

    private AuthentificationTest() {
    }

    /**
     * Crée le compte puis authentifie l'appelant sous cette identité.
     *
     * <p>⚠ Le compte existant est d'abord supprimé : {@code CurrentUserProvider} résout l'identité
     * par un {@code findByEmail} qui renvoie un {@code Optional}, et deux documents de même e-mail
     * — ce qu'un appel par test produirait — le feraient échouer en
     * {@code IncorrectResultSizeDataAccessException}.
     */
    public static void connecter(MongoTemplate mongoTemplate, String email, String role) {
        mongoTemplate.remove(new Query(Criteria.where("email").is(email)), User.class);
        User compte = new User();
        compte.setEmail(email);
        compte.setPassword("x");
        compte.setRole(role);
        mongoTemplate.save(compte);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    /** Raccourci : session super-administrateur. */
    public static void connecterSuperAdmin(MongoTemplate mongoTemplate) {
        connecter(mongoTemplate, EMAIL_SUPERADMIN, SUPERADMIN);
    }

    /**
     * À appeler en {@code @AfterEach} : le {@code SecurityContextHolder} est un ThreadLocal, une
     * session laissée ouverte fuiterait sur les tests suivants du même thread.
     */
    public static void deconnecter() {
        SecurityContextHolder.clearContext();
    }
}
