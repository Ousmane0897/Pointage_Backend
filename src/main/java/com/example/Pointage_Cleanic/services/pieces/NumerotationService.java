package com.example.Pointage_Cleanic.services.pieces;

import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.configurations.TimeConfig;
import com.example.Pointage_Cleanic.entities.pieces.CompteurPiece;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Numérotation unique des pièces (lot 1 / 1b, décision D4 ; CDC §3 « Services communs »,
 * §14 « Intégrité comptable »).
 *
 * <ul>
 *   <li>Une série par type et par exercice ({@code compteurs_pieces}, {@code _id = "BE-2026"}),
 *       incrémentée par {@code findAndModify($inc)}.</li>
 *   <li>Appelée <b>dans la transaction de validation</b> : si la validation échoue, l'incrément
 *       est annulé avec elle. Ni trou, ni doublon. Un appel hors transaction est refusé.</li>
 *   <li>Format {@code <TYPE>-<EXERCICE>-<NNNN>} : 4 chiffres minimum, sans troncature au-delà
 *       de 9999 ({@code BS-2027-12345}). Les numéros se trient sur (exercice, compteur), jamais
 *       sur la chaîne.</li>
 *   <li>Exercice = année civile à Dakar, à l'heure du serveur (horloge de
 *       {@link TimeConfig}).</li>
 * </ul>
 *
 * <p>Concurrence : deux validations simultanées du même type incrémentent le même document ;
 * la seconde transaction reçoit un conflit transitoire, rejoué par
 * {@code ExecuteurTransactionnel}.
 */
@Service
public class NumerotationService {

    public static final String COLLECTION = "compteurs_pieces";

    /** Numéro attribué, avec ses composantes. */
    public record NumeroPiece(String numero, int exercice, long compteur) {
    }

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    @Autowired
    public NumerotationService(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    /** Attribue le prochain numéro de la série (type, exercice courant). Exige une transaction. */
    public NumeroPiece attribuer(TypePiece type) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Numérotation hors transaction : elle doit être prise dans la transaction de validation");
        }
        int exercice = exerciceAu(clock.instant());
        Query query = new Query(Criteria.where("_id").is(cle(type, exercice)));
        Update update = new Update().inc("compteur", 1L);
        CompteurPiece c = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().upsert(true).returnNew(true), CompteurPiece.class);
        long compteur = c == null ? 1L : c.getCompteur();
        return new NumeroPiece(formater(type, exercice, compteur), exercice, compteur);
    }

    /**
     * Numérote la pièce si elle ne l'est pas encore, et ne fait rien sinon : une pièce antérieure
     * à 1b garde sa référence d'origine (pas de renumérotation de l'historique).
     *
     * @return le numéro de la pièce, attribué ou déjà présent
     */
    public String numeroter(PieceBase piece) {
        if (piece.getNumero() != null) {
            return piece.getNumero();
        }
        NumeroPiece n = attribuer(piece.getType());
        piece.setNumero(n.numero());
        piece.setExercice(n.exercice());
        piece.setCompteur(n.compteur());
        return n.numero();
    }

    /** Exercice (année civile) d'un instant, à l'heure de Dakar. */
    public static int exerciceAu(Instant instant) {
        return LocalDate.ofInstant(instant, TimeConfig.ZONE_METIER).getYear();
    }

    public static String cle(TypePiece type, int exercice) {
        return type.name() + "-" + exercice;
    }

    public static String formater(TypePiece type, int exercice, long compteur) {
        return String.format("%s-%d-%04d", type.name(), exercice, compteur);
    }
}
