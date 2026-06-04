package com.example.Pointage_Cleanic.DataLoader;

import org.bson.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migration : les anciens documents de la collection "utilisateur" stockaient les
 * sous-modules de modulesAutorises (CollecteLivraison, Absences, Pointages, Stock)
 * comme booléens simples. Le modèle actuel attend des objets — la lecture d'un
 * document legacy lève "No converter found capable of converting from type
 * [java.lang.Boolean]..." et masque les vraies erreurs métier (ex. 409 email existant).
 *
 * Convention : true → tous les sous-flags à true ; false → tous à false.
 * Idempotent : l'égalité Mongo est typée, is(true)/is(false) ne matche que les
 * booléens — les documents déjà au format objet ne sont jamais touchés.
 */
@Slf4j
@Component
@Order(900)
@RequiredArgsConstructor
public class ModulesAutorisesMigrationRunner implements CommandLineRunner {

    private static final String COLLECTION = "utilisateur";

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        long total = 0;
        total += migrer("CollecteLivraison", List.of("collecteBesoins", "suiviLivraison"));
        total += migrer("Absences", List.of("tempsReel", "historiqueAbsences"));
        total += migrer("Pointages", List.of("pointagesDuJour", "historiquePointages"));
        total += migrer("Stock", List.of("produits", "entrees", "sorties", "suivis",
                "historiquesEntrees", "historiquesSorties"));
        if (total > 0) {
            log.info("Migration modulesAutorises : {} champ(s) booléen(s) legacy convertis en objets", total);
        }
    }

    private long migrer(String champ, List<String> sousFlags) {
        long migres = 0;
        for (boolean valeur : new boolean[]{true, false}) {
            Document objet = new Document();
            sousFlags.forEach(f -> objet.append(f, valeur));
            Query query = Query.query(Criteria.where("modulesAutorises." + champ).is(valeur));
            Update update = Update.update("modulesAutorises." + champ, objet);
            migres += mongoTemplate.updateMulti(query, update, COLLECTION).getModifiedCount();
        }
        if (migres > 0) {
            log.info("Migration modulesAutorises.{} : {} document(s) migré(s)", champ, migres);
        }
        return migres;
    }
}
