package com.example.Pointage_Cleanic.services.pieces;

import com.example.Pointage_Cleanic.Enum.audit.ActionAudit;
import com.example.Pointage_Cleanic.entities.pieces.Piece;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import com.example.Pointage_Cleanic.services.audit.JournalAudit;
import com.example.Pointage_Cleanic.services.evenements.EvenementPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trace une transition de pièce (lot 1 / 1b, D4 + D5 + D6) : une ligne du journal d'audit et un
 * événement de domaine, tous deux dans la transaction de la transition. Le registre des pièces,
 * les notifications et les contre-effets sont produits par les consommateurs de l'événement.
 */
@Service
@RequiredArgsConstructor
public class TraceurPieces {

    /** Clés du payload commun des événements de pièce. */
    public static final String TYPE_PIECE = "typePiece";
    public static final String NUMERO = "numero";
    public static final String LIBELLE_NUMERO = "libelleNumero";
    public static final String EXERCICE = "exercice";
    public static final String COMPTEUR = "compteur";
    public static final String STATUT_PIECE = "statutPiece";
    public static final String STATUT_METIER = "statutMetier";
    public static final String MONTANT = "montant";
    public static final String CREE_LE = "creeLe";
    public static final String VALIDEE_LE = "valideeLe";
    public static final String MOTIF = "motif";

    private final EvenementPublisher publisher;
    private final JournalAudit journal;

    /**
     * @param avant état relu en début de transaction (copie), {@code null} pour une création
     * @param apres état enregistré
     * @param extra données propres au type de pièce, ajoutées au payload (peut être nul)
     */
    public void tracer(String typeEvenement, ActionAudit action, Object avant, Piece apres, String statutMetier,
                       String motif, Map<String, Object> extra) {
        journal.enregistrer(action, apres.getClass().getSimpleName(), apres.getId(), apres.libelleNumero(),
                avant, apres, motif);
        Map<String, Object> payload = payload(apres, statutMetier, motif);
        if (extra != null) {
            payload.putAll(extra);
        }
        publisher.publier(typeEvenement, apres.getPiece().getType().name(), apres.getId(), apres.siteIdPiece(),
                payload);
    }

    static Map<String, Object> payload(Piece piece, String statutMetier, String motif) {
        PieceBase p = piece.getPiece();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(TYPE_PIECE, p.getType().name());
        m.put(NUMERO, p.getNumero());
        m.put(LIBELLE_NUMERO, piece.libelleNumero());
        m.put(EXERCICE, p.getExercice());
        m.put(COMPTEUR, p.getCompteur());
        m.put(STATUT_PIECE, piece.statutPiece().name());
        m.put(STATUT_METIER, statutMetier);
        m.put(MONTANT, piece.montantPiece());
        // Dates en texte ISO : un Map relu depuis Mongo rendrait sinon des java.util.Date.
        m.put(CREE_LE, p.getCreeLe() == null ? null : p.getCreeLe().toString());
        m.put(VALIDEE_LE, p.getValideeLe() == null ? null : p.getValideeLe().toString());
        m.put(MOTIF, motif);
        return m;
    }
}
