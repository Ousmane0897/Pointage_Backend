package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.services.evenements.ConsommateurEvenement;
import com.example.Pointage_Cleanic.services.evenements.TypesEvenement;
import com.example.Pointage_Cleanic.services.pieces.TraceurPieces;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Contre-effets de l'annulation d'une pièce de stock (lot 1 / 1b, D4 + D5) : mouvements inverses
 * et soldes rétablis, dans la transaction du consommateur (avec la trace d'idempotence : rejouer
 * l'événement ne produit qu'un seul jeu de mouvements inverses).
 */
@Component
@RequiredArgsConstructor
public class ContrePassementStockConsommateur implements ConsommateurEvenement {

    public static final String NOM = "contre-passement-stock";

    private final ContrePassementStockService contrePassement;

    @Override
    public String nom() {
        return NOM;
    }

    @Override
    public Set<String> types() {
        return Set.of(TypesEvenement.PIECE_ANNULEE);
    }

    @Override
    public void traiter(EvenementDomaine evt) {
        String type = evt.getAgregatType();
        if (TypePiece.BE.name().equals(type) || TypePiece.BS.name().equals(type)) {
            contrePassement.contrePasserBon(evt.getAgregatId(),
                    (String) evt.getPayload().get(TraceurPieces.MOTIF), evt.getAuteurNom());
        }
    }
}
