package com.example.Pointage_Cleanic.services.pieces;

import com.example.Pointage_Cleanic.Enum.pieces.StatutPiece;
import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.entities.pieces.LignePieceRegistre;
import com.example.Pointage_Cleanic.services.evenements.ConsommateurEvenement;
import com.example.Pointage_Cleanic.services.evenements.TypesEvenement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

/**
 * Tient le registre des pièces à jour depuis les événements de pièce (lot 1 / 1b, D4). Une ligne
 * par {@code (type, pieceId)} ; un événement plus ancien que l'état enregistré (nouvel essai
 * arrivé en retard) est ignoré.
 */
@Component
@RequiredArgsConstructor
public class RegistrePiecesConsommateur implements ConsommateurEvenement {

    public static final String NOM = "registre-pieces";

    private static final Set<String> TYPES = Set.of(
            TypesEvenement.PIECE_CREEE, TypesEvenement.PIECE_MODIFIEE, TypesEvenement.PIECE_SOUMISE,
            TypesEvenement.PIECE_VALIDEE, TypesEvenement.PIECE_REFUSEE, TypesEvenement.PIECE_REPRISE,
            TypesEvenement.PIECE_ANNULEE, TypesEvenement.PIECE_ABANDONNEE);

    private final MongoTemplate mongoTemplate;

    @Override
    public String nom() {
        return NOM;
    }

    @Override
    public Set<String> types() {
        return TYPES;
    }

    @Override
    public void traiter(EvenementDomaine evt) {
        Map<String, Object> p = evt.getPayload();
        TypePiece type = TypePiece.valueOf((String) p.get(TraceurPieces.TYPE_PIECE));
        LignePieceRegistre ligne = mongoTemplate.findOne(new Query(Criteria.where("type").is(type)
                .and("pieceId").is(evt.getAgregatId())), LignePieceRegistre.class);
        if (ligne == null) {
            ligne = LignePieceRegistre.builder().type(type).pieceId(evt.getAgregatId()).build();
        } else if (ligne.getEtatAu() != null && ligne.getEtatAu().isAfter(evt.getSurvenuLe())) {
            return;
        }
        ligne.setNumero((String) p.get(TraceurPieces.NUMERO));
        ligne.setExercice(p.get(TraceurPieces.EXERCICE) instanceof Number n ? n.intValue() : null);
        ligne.setCompteur(p.get(TraceurPieces.COMPTEUR) instanceof Number n ? n.longValue() : null);
        ligne.setStatut(StatutPiece.valueOf((String) p.get(TraceurPieces.STATUT_PIECE)));
        ligne.setSiteId(evt.getSiteId());
        ligne.setMontant(p.get(TraceurPieces.MONTANT) instanceof Number n ? n.longValue() : 0L);
        ligne.setCreeLe(date(p.get(TraceurPieces.CREE_LE)));
        ligne.setValideeLe(date(p.get(TraceurPieces.VALIDEE_LE)));
        ligne.setEtatAu(evt.getSurvenuLe());
        ligne.setDernierEvenementId(evt.getId());
        mongoTemplate.save(ligne);
    }

    private static LocalDateTime date(Object valeur) {
        return valeur instanceof String s && !s.isBlank() ? LocalDateTime.parse(s) : null;
    }
}
