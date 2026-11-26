package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.NotificationStockDto;
import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.services.evenements.ConsommateurEvenement;
import com.example.Pointage_Cleanic.services.evenements.TypesEvenement;
import com.example.Pointage_Cleanic.services.pieces.TraceurPieces;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Notifications WebSocket des bons de stock (7.4), produites depuis les événements de pièce
 * (lot 1 / 1b, D5) : elles ne partent que si la transition a été enregistrée, et plus pendant la
 * requête. Remplace {@code ExecuteurTransactionnel.apresCommit}. Mêmes types de notification
 * qu'avant ({@code BON_SOUMIS}, {@code BON_VALIDE} + {@code BON_EFFECTIF}, {@code BON_REFUSE},
 * {@code BON_REPRIS}), plus {@code BON_ANNULE}.
 */
@Component
@RequiredArgsConstructor
public class NotificationsBonsConsommateur implements ConsommateurEvenement {

    public static final String NOM = "notifications-bons-stock";

    private static final Set<String> TYPES = Set.of(TypesEvenement.PIECE_SOUMISE, TypesEvenement.PIECE_VALIDEE,
            TypesEvenement.PIECE_REFUSEE, TypesEvenement.PIECE_REPRISE, TypesEvenement.PIECE_ANNULEE);

    private final StockNotificationService notificationService;

    @Override
    public String nom() {
        return NOM;
    }

    @Override
    public Set<String> types() {
        return TYPES;
    }

    @Override
    public boolean effetsExternes() {
        return true;
    }

    @Override
    public void traiter(EvenementDomaine evt) {
        String type = evt.getAgregatType();
        boolean entree = TypePiece.BE.name().equals(type);
        if (!entree && !TypePiece.BS.name().equals(type)) {
            return;
        }
        String libelle = entree ? "Bon d'entrée" : "Bon de sortie";
        String reference = (String) evt.getPayload().get(TraceurPieces.LIBELLE_NUMERO);
        String motif = (String) evt.getPayload().get(TraceurPieces.MOTIF);
        String bon = "Le bon " + reference;
        switch (evt.getType()) {
            case TypesEvenement.PIECE_SOUMISE -> notificationService.diffuserEtCiblerResponsableAchats(
                    notification("BON_SOUMIS", evt, entree, libelle + " soumis", bon + " attend validation."));
            case TypesEvenement.PIECE_VALIDEE -> List.of(
                    notification("BON_VALIDE", evt, entree, libelle + " validé", bon + " a été validé."),
                    notification("BON_EFFECTIF", evt, entree, "Stock mis à jour",
                            bon + " est effectif : stock mouvementé.")).forEach(notificationService::diffuser);
            case TypesEvenement.PIECE_REFUSEE -> notificationService.diffuser(
                    notification("BON_REFUSE", evt, entree, libelle + " refusé", bon + " a été refusé : " + motif));
            case TypesEvenement.PIECE_REPRISE -> notificationService.diffuser(
                    notification("BON_REPRIS", evt, entree, libelle + " repris",
                            bon + " est repassé en brouillon pour correction."));
            case TypesEvenement.PIECE_ANNULEE -> notificationService.diffuser(
                    notification("BON_ANNULE", evt, entree, libelle + " annulé",
                            bon + " a été annulé : " + motif));
            default -> {
                // type non notifié
            }
        }
    }

    private static NotificationStockDto notification(String type, EvenementDomaine evt, boolean entree,
                                                     String titre, String message) {
        return NotificationStockDto.builder()
                .type(type)
                .sens(entree ? "ENTREE" : "SORTIE")
                .bonId(evt.getAgregatId())
                .reference((String) evt.getPayload().get(TraceurPieces.LIBELLE_NUMERO))
                .titre(titre)
                .message(message)
                .dateEmission(LocalDateTime.now().toString())
                .build();
    }
}
