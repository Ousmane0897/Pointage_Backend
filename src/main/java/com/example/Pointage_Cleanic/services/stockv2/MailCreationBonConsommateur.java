package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.repositories.stockv2.BonEntreeRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.BonSortieRepository;
import com.example.Pointage_Cleanic.services.evenements.ConsommateurEvenement;
import com.example.Pointage_Cleanic.services.evenements.TypesEvenement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * E-mail récapitulatif à la création d'un bon (7.4), envoyé depuis l'événement
 * {@code PIECE_CREEE} (lot 1 / 1b, D5) et plus pendant la requête : la création ne dépend plus du
 * SMTP. {@link BonMailNotificationService} capte toujours les erreurs par destinataire (journal
 * WARN) : un destinataire en échec ne fait pas rejouer l'envoi aux autres.
 */
@Component
@RequiredArgsConstructor
public class MailCreationBonConsommateur implements ConsommateurEvenement {

    public static final String NOM = "mail-creation-bon-stock";

    private final BonMailNotificationService mailService;
    private final BonEntreeRepository bonEntreeRepository;
    private final BonSortieRepository bonSortieRepository;

    @Override
    public String nom() {
        return NOM;
    }

    @Override
    public Set<String> types() {
        return Set.of(TypesEvenement.PIECE_CREEE);
    }

    @Override
    public boolean effetsExternes() {
        return true;
    }

    @Override
    public void traiter(EvenementDomaine evt) {
        if (TypePiece.BE.name().equals(evt.getAgregatType())) {
            bonEntreeRepository.findById(evt.getAgregatId()).ifPresent(mailService::notifierCreationEntree);
        } else if (TypePiece.BS.name().equals(evt.getAgregatType())) {
            bonSortieRepository.findById(evt.getAgregatId()).ifPresent(mailService::notifierCreationSortie);
        }
    }
}
