package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bons de stock existants vers le modèle pièce (lot 1 / 1b, D4) : pose le bloc {@code piece} là
 * où il manque et remplit le registre des pièces.
 *
 * <ul>
 *   <li>{@code piece.numero} = la référence existante, telle quelle ({@code BE-20260617-001}) :
 *       pas de renumérotation de l'historique ; {@code exercice} et {@code compteur} restent nuls
 *       (série historique) ;</li>
 *   <li>auteurs et dates repris des champs actuels ({@code creePar*}, {@code createdAt} ;
 *       {@code validateur*} et {@code updatedAt} pour un bon EFFECTIF) ;</li>
 *   <li>registre : upsert sur {@code (type, pieceId)} ;</li>
 *   <li>rapport dans {@code migration_rapports} : comptages par statut et rejets (bon EFFECTIF
 *       sans référence, statut inconnu), rien n'est supprimé ni bloqué.</li>
 * </ul>
 * Rejouable : un bon qui a déjà son bloc {@code piece} n'est pas retouché.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PiecesBonsExistantsMigration {

    public static final String ID = "0012-pieces-bons-existants";
    static final String RAPPORTS = "migration_rapports";
    static final String REGISTRE = "registre_pieces";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public void executer() {
        Map<String, Integer> comptages = new TreeMap<>();
        List<Document> rejets = new ArrayList<>();
        int traites = traiter("stockv2_bons_entree", TypePiece.BE, "siteDestinationId", comptages, rejets)
                + traiter("stockv2_bons_sortie", TypePiece.BS, "siteSourceId", comptages, rejets);
        if (traites == 0) {
            return; // rien à porter (base vide ou déjà migrée) : aucune écriture
        }
        mongoTemplate.insert(new Document("migrationId", ID)
                .append("le", Date.from(clock.instant()))
                .append("traites", traites)
                .append("comptages", new Document(new java.util.LinkedHashMap<>(comptages)))
                .append("rejets", rejets), RAPPORTS);
        log.info("{} : {} bon(s) portés vers le modèle pièce, {} rejet(s) signalé(s) dans {}",
                ID, traites, rejets.size(), RAPPORTS);
    }

    private int traiter(String collection, TypePiece type, String champSite, Map<String, Integer> comptages,
                        List<Document> rejets) {
        List<Document> bons = mongoTemplate.find(Query.query(Criteria.where("piece").exists(false)),
                Document.class, collection);
        Date maintenant = Date.from(clock.instant());
        for (Document bon : bons) {
            Object id = bon.get("_id");
            String statut = bon.getString("statut");
            String reference = bon.getString("reference");
            comptages.merge(type.name() + "." + statut, 1, Integer::sum);

            String statutPiece;
            try {
                statutPiece = StatutBon.valueOf(statut).statutPiece().name();
            } catch (RuntimeException e) {
                rejets.add(new Document("collection", collection).append("id", id)
                        .append("motif", "statut inconnu : " + statut));
                continue;
            }
            boolean effectif = StatutBon.EFFECTIF.name().equals(statut);
            if (effectif && reference == null) {
                rejets.add(new Document("collection", collection).append("id", id)
                        .append("motif", "bon EFFECTIF sans référence"));
            }

            Document piece = new Document("type", type.name())
                    .append("numero", reference)
                    .append("creePar", auteur(bon.getString("creeParId"), bon.getString("creeParNom")))
                    .append("creeLe", bon.get("createdAt"));
            if (effectif) {
                piece.append("valideePar", auteur(bon.getString("validateurId"), bon.getString("validateurNom")))
                        .append("valideeLe", bon.get("updatedAt"));
            }
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)), Update.update("piece", piece),
                    collection);

            String pieceId = id.toString();
            Number montant = bon.get("montantTotal", Number.class);
            mongoTemplate.upsert(Query.query(Criteria.where("type").is(type.name()).and("pieceId").is(pieceId)),
                    new Update()
                            .set("numero", reference)
                            .set("statut", statutPiece)
                            .set("siteId", bon.getString(champSite))
                            .set("montant", montant == null ? 0L : montant.longValue())
                            .set("creeLe", bon.get("createdAt"))
                            .set("valideeLe", effectif ? bon.get("updatedAt") : null)
                            .set("etatAu", maintenant)
                            .setOnInsert("amont", List.of())
                            .setOnInsert("aval", List.of()),
                    REGISTRE);
        }
        return bons.size();
    }

    private static Document auteur(String compteId, String nom) {
        if (compteId == null && nom == null) {
            return null;
        }
        return new Document("compteId", compteId).append("nom", nom);
    }
}
