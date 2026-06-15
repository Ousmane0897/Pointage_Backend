package com.example.Pointage_Cleanic.DataLoader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Nettoyage : depuis l'alignement de {@code modulesAutorises} sur la nouvelle structure
 * frontend (dashboard / admin / rh / productionChimie / terrain), les anciens flags de
 * permission n'existent plus dans le modèle. La lecture des documents legacy ne plante pas
 * (Jackson + le converter Mongo ignorent les champs inconnus), mais ce runner retire les
 * clés obsolètes pour garder la collection propre.
 *
 * Sûr et idempotent : {@code $unset} sur une clé absente est un no-op, et seules des
 * permissions obsolètes sont supprimées — aucune donnée métier n'est touchée.
 */
@Slf4j
@Component
@Order(900)
@RequiredArgsConstructor
public class ModulesAutorisesCleanupRunner implements CommandLineRunner {

    private static final String COLLECTION = "utilisateur";

    private static final List<String> CHAMPS_MORTS = List.of(
            "StatistiquesAgences", "Planifications", "Calendrier", "JourFeries",
            "Employes", "Agences",
            "CollecteLivraison", "Absences", "Pointages", "Stock");

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        Update update = new Update();
        CHAMPS_MORTS.forEach(champ -> update.unset("modulesAutorises." + champ));

        long modifies = mongoTemplate
                .updateMulti(new Query(), update, COLLECTION)
                .getModifiedCount();

        if (modifies > 0) {
            log.info("Cleanup modulesAutorises : {} document(s) nettoyé(s) des flags obsolètes", modifies);
        }
    }
}
