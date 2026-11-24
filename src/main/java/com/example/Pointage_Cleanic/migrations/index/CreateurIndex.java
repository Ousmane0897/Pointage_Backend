package com.example.Pointage_Cleanic.migrations.index;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Crée les index d'un module (lot 1 / 1a, décision D3), appelé par les ChangeUnits d'index.
 *
 * <ul>
 *   <li>index déjà présent sous le même nom avec les mêmes clés : rien à faire ;</li>
 *   <li>même nom mais autre définition, ou mêmes clés sous un autre nom : <b>conflit</b> ;</li>
 *   <li>index unique : recherche préalable des doublons ({@code $group}, {@code count > 1}), dans le
 *       périmètre du filtre partiel s'il y en a un ;</li>
 *   <li>doublon ou conflit : rapport dans {@code migration_rapports} (identifiants et valeurs,
 *       pour la décision humaine), puis <b>échec</b> : la ChangeUnit échoue, le démarrage est
 *       refusé. Rien n'est dédoublonné ni supprimé. Les journaux ne donnent que des nombres.</li>
 * </ul>
 * Les index valides du module sont créés avant l'échec : une ChangeUnit d'index n'est pas
 * transactionnelle (MongoDB l'interdit) et la création est idempotente, donc rejouable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateurIndex {

    static final String RAPPORTS = "migration_rapports";
    private static final int ECHANTILLON = 20;

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public void creer(String module, String migrationId) {
        creer(IndexesAttendus.duModule(module), migrationId);
    }

    void creer(List<IndexAttendu> index, String migrationId) {
        List<Document> rejets = new ArrayList<>();
        int crees = 0;
        int presents = 0;
        for (IndexAttendu attendu : index) {
            MongoCollection<Document> collection = mongoTemplate.getCollection(attendu.collection());
            Optional<Document> conflit = conflit(collection, attendu);
            if (conflit.isPresent()) {
                rejets.add(conflit.get());
                continue;
            }
            if (existe(collection, attendu)) {
                presents++;
                continue;
            }
            if (attendu.unique()) {
                List<Document> doublons = doublons(collection, attendu);
                if (!doublons.isEmpty()) {
                    rejets.add(new Document("motif", "DOUBLONS")
                            .append("collection", attendu.collection())
                            .append("index", attendu.nom())
                            .append("valeursEnDouble", doublons));
                    continue;
                }
            }
            IndexOptions options = new IndexOptions().name(attendu.nom()).unique(attendu.unique());
            if (attendu.filtrePartiel() != null) {
                options.partialFilterExpression(attendu.filtrePartiel());
            }
            collection.createIndex(attendu.cles(), options);
            crees++;
        }

        if (!rejets.isEmpty()) {
            mongoTemplate.insert(new Document("migrationId", migrationId)
                    .append("executeLe", LocalDateTime.now(clock))
                    .append("comptages", new Document("attendus", index.size())
                            .append("crees", crees).append("dejaPresents", presents).append("rejetes", rejets.size()))
                    .append("rejets", rejets), RAPPORTS);
            log.error("Index {} : {} index refusé(s) (doublons ou conflit de définition). Démarrage refusé : "
                    + "voir la collection {} (migrationId {}).", migrationId, rejets.size(), RAPPORTS, migrationId);
            throw new IllegalStateException("Migration " + migrationId + " : " + rejets.size()
                    + " index non créé(s) (doublons ou conflit de définition). Détail dans "
                    + RAPPORTS + ", migrationId " + migrationId + ".");
        }
        log.info("Index {} : {} créé(s), {} déjà présent(s).", migrationId, crees, presents);
    }

    private static List<Document> indexEnBase(MongoCollection<Document> collection) {
        return collection.listIndexes().into(new ArrayList<>());
    }

    private static boolean existe(MongoCollection<Document> collection, IndexAttendu attendu) {
        return indexEnBase(collection).stream().anyMatch(i -> attendu.nom().equals(i.getString("name")));
    }

    /** Même nom et autre définition, ou mêmes clés sous un autre nom (MongoDB refuserait). */
    private static Optional<Document> conflit(MongoCollection<Document> collection, IndexAttendu attendu) {
        for (Document enBase : indexEnBase(collection)) {
            String nom = enBase.getString("name");
            boolean memeNom = attendu.nom().equals(nom);
            boolean memesCles = !attendu.estTexte() && attendu.cles().equals(enBase.get("key", Document.class));
            if (memeNom && !memeDefinition(attendu, enBase)) {
                return Optional.of(new Document("motif", "DEFINITION_DIFFERENTE")
                        .append("collection", attendu.collection()).append("index", attendu.nom())
                        .append("enBase", enBase));
            }
            if (!memeNom && memesCles && !"_id_".equals(nom)) {
                return Optional.of(new Document("motif", "MEMES_CLES_AUTRE_NOM")
                        .append("collection", attendu.collection()).append("index", attendu.nom())
                        .append("enBase", enBase));
            }
        }
        return Optional.empty();
    }

    static boolean memeDefinition(IndexAttendu attendu, Document enBase) {
        boolean uniqueEnBase = Boolean.TRUE.equals(enBase.getBoolean("unique"));
        Object filtreEnBase = enBase.get("partialFilterExpression");
        boolean filtreIdentique = attendu.filtrePartiel() == null
                ? filtreEnBase == null
                : attendu.filtrePartiel().equals(filtreEnBase);
        boolean clesIdentiques = attendu.estTexte() || attendu.cles().equals(enBase.get("key", Document.class));
        return uniqueEnBase == attendu.unique() && filtreIdentique && clesIdentiques;
    }

    /** Valeurs présentes plusieurs fois, dans le périmètre de l'index (échantillon). */
    static List<Document> doublons(MongoCollection<Document> collection, IndexAttendu attendu) {
        Document groupe = new Document();
        for (Map.Entry<String, Object> cle : attendu.cles().entrySet()) {
            // Pour un index unique, champ absent et champ null sont la même valeur : $ifNull les
            // regroupe (sinon un doublon « absent / null » passerait inaperçu, puis la création
            // échouerait en DuplicateKey, sans rapport).
            groupe.append(cle.getKey().replace('.', '_'),
                    new Document("$ifNull", java.util.Arrays.asList("$" + cle.getKey(), null)));
        }
        List<Document> pipeline = new ArrayList<>();
        if (attendu.filtrePartiel() != null) {
            pipeline.add(new Document("$match", attendu.filtrePartiel()));
        }
        pipeline.add(new Document("$group", new Document("_id", groupe)
                .append("nombre", new Document("$sum", 1))
                .append("ids", new Document("$push", "$_id"))));
        pipeline.add(new Document("$match", new Document("nombre", new Document("$gt", 1))));
        pipeline.add(new Document("$limit", ECHANTILLON));
        pipeline.add(new Document("$project", new Document("_id", 0).append("valeur", "$_id")
                .append("nombre", 1).append("ids", new Document("$slice", List.of("$ids", 10)))));
        return collection.aggregate(pipeline).allowDiskUse(true).into(new ArrayList<>());
    }
}
