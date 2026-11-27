package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Stock;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

/**
 * Comptes des essais de bout en bout (profil {@code e2e} seulement, jamais en production) :
 * un super-administrateur et un compte limité au module Stock (tous les drapeaux {@code stock.*},
 * rien d'autre). Mots de passe lus dans {@code E2E_SUPERADMIN_PASSWORD} et
 * {@code E2E_STOCK_PASSWORD} : jamais dans le code. Base refusée si son nom ne contient pas
 * {@code e2e} ({@link E2eGardeFou}).
 */
@Slf4j
@Component
@Profile("e2e")
@RequiredArgsConstructor
public class E2eComptesSeed {

    public static final String EMAIL_SUPERADMIN = "superadmin@e2e.pointic.test";
    public static final String EMAIL_STOCK = "stock@e2e.pointic.test";

    private final MongoTemplate mongoTemplate;
    private final LoginRepository loginRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${E2E_SUPERADMIN_PASSWORD:}")
    private String motDePasseSuperadmin;

    @Value("${E2E_STOCK_PASSWORD:}")
    private String motDePasseStock;

    public void executer() {
        E2eGardeFou.verifierBase(mongoTemplate.getDb().getName());
        String mdpSuperadmin = E2eGardeFou.exiger("E2E_SUPERADMIN_PASSWORD", motDePasseSuperadmin);
        String mdpStock = E2eGardeFou.exiger("E2E_STOCK_PASSWORD", motDePasseStock);

        User superadmin = new User();
        superadmin.setEmail(EMAIL_SUPERADMIN);
        superadmin.setPassword(passwordEncoder.encode(mdpSuperadmin));
        superadmin.setRole("SUPERADMIN");
        superadmin.setMustChangePassword(false);
        loginRepository.save(superadmin);

        String hashStock = passwordEncoder.encode(mdpStock);
        User loginStock = new User();
        loginStock.setEmail(EMAIL_STOCK);
        loginStock.setPassword(hashStock);
        loginStock.setRole(RoleAdmin.CONTROLEUR_STOCK.name());
        loginStock.setMustChangePassword(false);
        loginRepository.save(loginStock);

        ModulesAutorises modules = new ModulesAutorises();
        modules.setDashboard(false);
        modules.setAdmin(false);
        modules.setStock(stockComplet());

        Utilisateur stock = new Utilisateur();
        stock.setPrenom("Essai");
        stock.setNom("Stock");
        stock.setEmail(EMAIL_STOCK);
        stock.setPassword(hashStock);
        stock.setPoste("Contrôleur de stock (essais e2e)");
        stock.setRole(RoleAdmin.CONTROLEUR_STOCK);
        stock.setMustChangePassword(false);
        stock.setModulesAutorises(modules);
        stock.setActive(true);
        utilisateurRepository.save(stock);
        log.info("Profil e2e : comptes d'essai créés ({}, {}).", EMAIL_SUPERADMIN, EMAIL_STOCK);
    }

    /** Tous les sous-drapeaux du module Stock à {@code true}, sans en tenir la liste à la main. */
    static Stock stockComplet() {
        Stock stock = new Stock();
        for (Field champ : Stock.class.getDeclaredFields()) {
            if (champ.getType() == boolean.class) {
                champ.setAccessible(true);
                try {
                    champ.setBoolean(stock, true);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        return stock;
    }
}
