package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.Enum.rh.CategorieCritere;
import com.example.Pointage_Cleanic.Enum.rh.TypeFormateur;
import com.example.Pointage_Cleanic.entities.rh.CritereEvaluation;
import com.example.Pointage_Cleanic.entities.rh.Formation;
import com.example.Pointage_Cleanic.entities.rh.GrilleEvaluation;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.repositories.rh.FormationRepository;
import com.example.Pointage_Cleanic.repositories.rh.GrilleEvaluationRepository;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final LoginRepository loginRepository;
    private final PasswordEncoder passwordEncoder;

    private final GrilleEvaluationRepository grilleEvaluationRepository;
    private final FormationRepository formationRepository;

    static final String SUPERADMIN_EMAIL = "diarra.niang@cleanicsenegal.com";

    @Value("${SUPERADMIN_INITIAL_PASSWORD:}")
    private String superadminInitialPassword;


    @Override
    public void run(String... args) {
        seedSuperadmin();
        seedGrilleEvaluationParDefaut();
        seedFormationsExemples();
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

    private void seedGrilleEvaluationParDefaut() {
        if (grilleEvaluationRepository.count() > 0) {
            return;
        }
        GrilleEvaluation grille = GrilleEvaluation.builder()
                .titre("Grille générique Cleanic")
                .description("Grille d'évaluation par défaut applicable à tous les postes. "
                        + "Total des poids = 100.")
                .postesConcernes(List.of())
                .departementsConcernes(List.of())
                .criteres(List.of(
                        CritereEvaluation.builder()
                                .code("EXPERTISE_TECHNIQUE")
                                .libelle("Expertise technique")
                                .description("Maîtrise du métier et des outils")
                                .poids(30)
                                .categorie(CategorieCritere.TECHNIQUE)
                                .build(),
                        CritereEvaluation.builder()
                                .code("AUTONOMIE")
                                .libelle("Autonomie")
                                .description("Capacité à gérer ses tâches sans supervision")
                                .poids(20)
                                .categorie(CategorieCritere.TECHNIQUE)
                                .build(),
                        CritereEvaluation.builder()
                                .code("COMMUNICATION")
                                .libelle("Communication")
                                .description("Qualité des échanges avec collègues et hiérarchie")
                                .poids(15)
                                .categorie(CategorieCritere.COMPORTEMENTAL)
                                .build(),
                        CritereEvaluation.builder()
                                .code("TRAVAIL_EQUIPE")
                                .libelle("Travail en équipe")
                                .description("Collaboration et entraide")
                                .poids(15)
                                .categorie(CategorieCritere.COMPORTEMENTAL)
                                .build(),
                        CritereEvaluation.builder()
                                .code("ATTEINTE_OBJECTIFS")
                                .libelle("Atteinte des objectifs")
                                .description("Réalisation des objectifs de la période")
                                .poids(20)
                                .categorie(CategorieCritere.OBJECTIFS)
                                .build()
                ))
                .actif(true)
                .dateCreation(LocalDate.now())
                .build();
        grilleEvaluationRepository.save(grille);
        System.out.println("Grille d'évaluation par défaut créée");
    }

    private void seedFormationsExemples() {
        if (formationRepository.count() > 0) {
            return;
        }
        List<Formation> exemples = List.of(
                Formation.builder()
                        .titre("Sécurité et hygiène au travail")
                        .description("Rappel des règles de sécurité et d'hygiène sur site")
                        .competencesVisees(List.of("Sécurité", "Hygiène", "Port des EPI"))
                        .dureeHeures(8)
                        .typeFormateur(TypeFormateur.INTERNE)
                        .formateurNom("Responsable QHSE")
                        .coutFcfa(0L)
                        .actif(true)
                        .dateCreation(LocalDate.now())
                        .build(),
                Formation.builder()
                        .titre("Nettoyage industriel — techniques et produits")
                        .description("Formation au poste : techniques de nettoyage et usage des produits")
                        .competencesVisees(List.of("Nettoyage industriel", "Dosage produits", "Machines"))
                        .dureeHeures(16)
                        .typeFormateur(TypeFormateur.INTERNE)
                        .formateurNom("Chef d'équipe senior")
                        .coutFcfa(0L)
                        .actif(true)
                        .dateCreation(LocalDate.now())
                        .build(),
                Formation.builder()
                        .titre("Management d'équipe")
                        .description("Fondamentaux du management pour chefs d'équipe")
                        .competencesVisees(List.of("Leadership", "Délégation", "Gestion des conflits"))
                        .dureeHeures(24)
                        .typeFormateur(TypeFormateur.EXTERNE)
                        .formateurNom("Cabinet de formation externe")
                        .coutFcfa(450000L)
                        .actif(true)
                        .dateCreation(LocalDate.now())
                        .build()
        );
        formationRepository.saveAll(exemples);
        System.out.println("Formations d'exemple créées (" + exemples.size() + ")");
    }
}