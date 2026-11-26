package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Inventaires existants vers le modèle pièce (lot 1 / 1b, D4), comme 0012 pour les bons : bloc
 * {@code piece} posé là où il manque ({@code numero} = référence existante, série historique),
 * auteur = responsable, registre des pièces rempli, rapport dans {@code migration_rapports}
 * (comptages par statut, rejets : inventaire clôturé sans référence, statut inconnu). Rien n'est
 * supprimé. Rejouable : un inventaire qui a déjà son bloc n'est pas retouché, et rien n'est
 * écrit s'il n'y a rien à porter.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PiecesInventairesExistantsMigration {

    public static final String ID = "0013-pieces-inventaires-existants";
    static final String COLLECTION = "stockv2_inventaires";

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public void executer() {
        List<Document> inventaires = mongoTemplate.find(Query.query(Criteria.where("piece").exists(false)),
                Document.class, COLLECTION);
        if (inventaires.isEmpty()) {
            return;
        }
        Map<String, Integer> comptages = new TreeMap<>();
        List<Document> rejets = new ArrayList<>();
        Date maintenant = Date.from(clock.instant());
        for (Document inv : inventaires) {
            Object id = inv.get("_id");
            String statut = inv.getString("statut");
            String reference = inv.getString("reference");
            comptages.merge(TypePiece.INV.name() + "." + statut, 1, Integer::sum);

            String statutPiece;
            try {
                statutPiece = StatutInventaire.valueOf(statut).statutPiece().name();
            } catch (RuntimeException e) {
                rejets.add(new Document("collection", COLLECTION).append("id", id)
                        .append("motif", "statut inconnu : " + statut));
                continue;
            }
            boolean cloture = StatutInventaire.CLOTURE.name().equals(statut);
            if (cloture && reference == null) {
                rejets.add(new Document("collection", COLLECTION).append("id", id)
                        .append("motif", "inventaire CLOTURE sans référence"));
            }
            String responsable = inv.getString("responsable");
            Document piece = new Document("type", TypePiece.INV.name())
                    .append("numero", reference)
                    .append("creePar", responsable == null ? null : new Document("compteId", null).append("nom", responsable))
                    .append("creeLe", inv.get("createdAt"));
            if (cloture) {
                piece.append("valideeLe", inv.get("updatedAt"));
            }
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(id)), Update.update("piece", piece),
                    COLLECTION);
            mongoTemplate.upsert(Query.query(Criteria.where("type").is(TypePiece.INV.name())
                            .and("pieceId").is(id.toString())),
                    new Update()
                            .set("numero", reference)
                            .set("statut", statutPiece)
                            .set("siteId", inv.getString("siteId"))
                            .set("montant", 0L)
                            .set("creeLe", inv.get("createdAt"))
                            .set("valideeLe", cloture ? inv.get("updatedAt") : null)
                            .set("etatAu", maintenant)
                            .setOnInsert("amont", List.of())
                            .setOnInsert("aval", List.of()),
                    PiecesBonsExistantsMigration.REGISTRE);
        }
        mongoTemplate.insert(new Document("migrationId", ID)
                .append("le", maintenant)
                .append("traites", inventaires.size())
                .append("comptages", new Document(new LinkedHashMap<>(comptages)))
                .append("rejets", rejets), PiecesBonsExistantsMigration.RAPPORTS);
        log.info("{} : {} inventaire(s) portés vers le modèle pièce, {} rejet(s) signalé(s)",
                ID, inventaires.size(), rejets.size());
    }
}
