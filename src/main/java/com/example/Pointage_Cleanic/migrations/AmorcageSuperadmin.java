package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;



/**
 * Amorçage du compte super-administrateur au démarrage (ex-{@code DataLoader.seedSuperadmin}).
 *
 * <p>Volontairement <b>hors Mongock</b> (lot 1 / 1a, écart documenté avec D2) : il dépend d'une
 * variable d'environnement. En ChangeUnit, il serait inscrit « appliqué » même quand il est
 * sauté faute de mot de passe, et une base neuve resterait sans compte d'administration. Il
 * reste donc un {@link CommandLineRunner} idempotent, comportement inchangé. Il disparaîtra avec
 * la collection {@code comptes} (sous-branche 1g).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmorcageSuperadmin implements CommandLineRunner {

    private final LoginRepository loginRepository;
    private final PasswordEncoder passwordEncoder;

    static final String SUPERADMIN_EMAIL = "diarra.niang@cleanicsenegal.com";

    @Value("${SUPERADMIN_INITIAL_PASSWORD:}")
    private String superadminInitialPassword;


    @Override
    public void run(String... args) {
        seedSuperadmin();
    }

    /**
     * Crée le super-administrateur initial s'il n'existe pas encore.
     *
     * <p>Le mot de passe initial vient de la variable d'environnement
     * {@code SUPERADMIN_INITIAL_PASSWORD} — jamais du code. Absente ou vide : aucun compte n'est
     * créé (le démarrage continue). Le compte doit changer son mot de passe à la première
     * connexion ({@code mustChangePassword=true}).
     */
    void seedSuperadmin() {
        if (loginRepository.findByEmail(SUPERADMIN_EMAIL).isPresent()) {
            return;
        }
        if (superadminInitialPassword == null || superadminInitialPassword.isBlank()) {
            log.warn("Super-administrateur initial non créé : SUPERADMIN_INITIAL_PASSWORD n'est pas défini.");
            return;
        }
        User user = new User();
        user.setEmail(SUPERADMIN_EMAIL);
        user.setPassword(passwordEncoder.encode(superadminInitialPassword));
        user.setRole("SUPERADMIN");
        user.setMustChangePassword(true);
        loginRepository.save(user);
        log.info("Super-administrateur initial créé (changement de mot de passe exigé à la première connexion).");
    }
}
