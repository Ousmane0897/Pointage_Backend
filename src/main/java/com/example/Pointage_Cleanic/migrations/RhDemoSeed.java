package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.Enum.rh.CategorieCritere;
import com.example.Pointage_Cleanic.Enum.rh.TypeFormateur;
import com.example.Pointage_Cleanic.entities.rh.CritereEvaluation;
import com.example.Pointage_Cleanic.entities.rh.Formation;
import com.example.Pointage_Cleanic.entities.rh.GrilleEvaluation;
import com.example.Pointage_Cleanic.repositories.rh.FormationRepository;
import com.example.Pointage_Cleanic.repositories.rh.GrilleEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Données de DÉMONSTRATION du module RH : une grille d'évaluation générique et trois
 * formations d'exemple. Profil {@code demo} seulement (lot 1 / 1a, décision D2) : jamais en
 * production. Extrait de l'ancien {@code DataLoader}. Idempotent ({@code count() > 0} → rien).
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class RhDemoSeed {

    private final GrilleEvaluationRepository grilleEvaluationRepository;
    private final FormationRepository formationRepository;

    public void executer() {
        seedGrilleEvaluationParDefaut();
        seedFormationsExemples();
    }

    void seedGrilleEvaluationParDefaut() {
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
        log.info("Démo : grille d'évaluation générique créée");
    }

    void seedFormationsExemples() {
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
        log.info("Démo : {} formations d'exemple créées", exemples.size());
    }
}
