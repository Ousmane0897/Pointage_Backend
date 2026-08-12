package com.example.Pointage_Cleanic.DataLoader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Migration : {@code modulesAutorises.rh} passe de booléen à objet de 17 sous-flags
 * (cf. {@link com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Rh}).
 *
 * <p>Les documents existants stockent {@code rh} comme booléen BSON. Une fois le champ typé
 * {@code Rh}, le converter Mongo (≠ Jackson) planterait à la lecture booléen→objet. Ce runner
 * réécrit donc, en BSON brut (jamais de lecture typée {@code Utilisateur}), tout {@code rh}
 * booléen restant :
 * <ul>
 *   <li>{@code rh: true}  ⇒ objet aux 17 sous-flags à {@code true} (accès RH complet conservé) ;</li>
 *   <li>{@code rh: false} ⇒ objet aux 17 sous-flags à {@code false} ;</li>
 *   <li>{@code rh} absent ⇒ laissé absent ({@code null} = pas d'accès RH, omis du claim).</li>
 * </ul>
 *
 * <p>Idempotent : après réécriture {@code rh} est un sous-document, les critères
 * {@code is(true)/is(false)} ne matchent plus → les exécutions suivantes sont des no-op.
 * Placé après {@link ModulesAutorisesCleanupRunner} ({@code @Order(900)}).
 */
@Slf4j
@Component
@Order(901)
@RequiredArgsConstructor
public class RhModuleMigrationRunner implements CommandLineRunner {

    private static final String COLLECTION = "utilisateur";
    private static final String CHAMP_RH = "modulesAutorises.rh";

    /** Les 19 clés camelCase du contrat front (cf. {@code Rh}). */
    private static final List<String> SOUS_FLAGS = List.of(
            // 6.1 Gestion du Personnel
            "dossierEmploye", "contrats", "organigramme", "documents",
            // 6.2 Temps & Présences
            "pointageCentralise", "absences", "conges", "congesValidation", "congesMesDemandes",
            "heuresSupplementaires", "recapitulatif",
            // 6.3 Paie
            "grilleSalariale", "calculBulletin", "historiquePaies", "declarations",
            // 6.4 Développement RH
            "formations", "evaluations", "sanctions", "tableauBordRh");

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        long versTrue = migrer(true);
        long versFalse = migrer(false);

        if (versTrue > 0 || versFalse > 0) {
            // Nombre de flags tiré de SOUS_FLAGS, pour qu'il ne dérive pas à chaque ajout.
            log.info("Migration modulesAutorises.rh booléen→objet : {} doc(s) rh=true → {} flags true, "
                    + "{} doc(s) rh=false → {} flags false",
                    versTrue, SOUS_FLAGS.size(), versFalse, SOUS_FLAGS.size());
        }
    }

    private long migrer(boolean valeur) {
        Query query = new Query(Criteria.where(CHAMP_RH).is(valeur));
        Update update = new Update().set(CHAMP_RH, rhDocument(valeur));
        return mongoTemplate.updateMulti(query, update, COLLECTION).getModifiedCount();
    }

    private static Document rhDocument(boolean valeur) {
        Document doc = new Document();
        SOUS_FLAGS.forEach(flag -> doc.put(flag, valeur));
        return doc;
    }
}