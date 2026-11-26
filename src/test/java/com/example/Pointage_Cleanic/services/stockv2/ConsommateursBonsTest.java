package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.NotificationStockDto;
import com.example.Pointage_Cleanic.entities.evenements.EvenementDomaine;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.repositories.stockv2.BonEntreeRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.BonSortieRepository;
import com.example.Pointage_Cleanic.services.evenements.TypesEvenement;
import com.example.Pointage_Cleanic.services.pieces.TraceurPieces;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Consommateurs des événements de bons (lot 1 / 1b, D5) : les notifications WebSocket et l'e-mail
 * de création partent de l'événement, avec les mêmes types de notification qu'avant 1b.
 */
class ConsommateursBonsTest {

    private final StockNotificationService notifications = mock(StockNotificationService.class);
    private final NotificationsBonsConsommateur consommateur = new NotificationsBonsConsommateur(notifications);

    private static EvenementDomaine evt(String type, String agregatType, String motif) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(TraceurPieces.LIBELLE_NUMERO, "BS-2026-0007");
        payload.put(TraceurPieces.MOTIF, motif);
        return EvenementDomaine.builder().id("e1").type(type).agregatType(agregatType).agregatId("bon-1")
                .payload(payload).build();
    }

    @Test
    void soumission_diffusee_et_ciblee_sur_le_responsable_achats() {
        consommateur.traiter(evt(TypesEvenement.PIECE_SOUMISE, "BS", null));

        ArgumentCaptor<NotificationStockDto> dto = ArgumentCaptor.forClass(NotificationStockDto.class);
        verify(notifications).diffuserEtCiblerResponsableAchats(dto.capture());
        assertThat(dto.getValue().getType()).isEqualTo("BON_SOUMIS");
        assertThat(dto.getValue().getSens()).isEqualTo("SORTIE");
        assertThat(dto.getValue().getBonId()).isEqualTo("bon-1");
        assertThat(dto.getValue().getReference()).isEqualTo("BS-2026-0007");
    }

    @Test
    void validation_donne_bon_valide_puis_bon_effectif() {
        consommateur.traiter(evt(TypesEvenement.PIECE_VALIDEE, "BE", null));

        ArgumentCaptor<NotificationStockDto> dto = ArgumentCaptor.forClass(NotificationStockDto.class);
        verify(notifications, times(2)).diffuser(dto.capture());
        assertThat(dto.getAllValues()).extracting(NotificationStockDto::getType)
                .containsExactly("BON_VALIDE", "BON_EFFECTIF");
        assertThat(dto.getAllValues()).allSatisfy(d -> assertThat(d.getSens()).isEqualTo("ENTREE"));
    }

    @Test
    void refus_reprise_et_annulation_notifies_avec_motif() {
        consommateur.traiter(evt(TypesEvenement.PIECE_REFUSEE, "BS", "quantité erronée"));
        consommateur.traiter(evt(TypesEvenement.PIECE_REPRISE, "BS", null));
        consommateur.traiter(evt(TypesEvenement.PIECE_ANNULEE, "BS", "erreur de saisie"));

        ArgumentCaptor<NotificationStockDto> dto = ArgumentCaptor.forClass(NotificationStockDto.class);
        verify(notifications, times(3)).diffuser(dto.capture());
        List<NotificationStockDto> envoyees = dto.getAllValues();
        assertThat(envoyees).extracting(NotificationStockDto::getType)
                .containsExactly("BON_REFUSE", "BON_REPRIS", "BON_ANNULE");
        assertThat(envoyees.get(0).getMessage()).contains("quantité erronée");
        assertThat(envoyees.get(2).getMessage()).contains("erreur de saisie");
    }

    @Test
    void pieces_d_autres_modules_ignorees() {
        consommateur.traiter(evt(TypesEvenement.PIECE_VALIDEE, "INV", null));
        verify(notifications, never()).diffuser(any());
        verify(notifications, never()).diffuserEtCiblerResponsableAchats(any());
    }

    @Test
    void mail_de_creation_envoye_depuis_l_evenement() {
        BonMailNotificationService mail = mock(BonMailNotificationService.class);
        BonEntreeRepository entrees = mock(BonEntreeRepository.class);
        BonSortieRepository sorties = mock(BonSortieRepository.class);
        BonEntree be = BonEntree.builder().id("be-1").build();
        BonSortie bs = BonSortie.builder().id("bs-1").build();
        when(entrees.findById("be-1")).thenReturn(Optional.of(be));
        when(sorties.findById("bs-1")).thenReturn(Optional.of(bs));
        MailCreationBonConsommateur c = new MailCreationBonConsommateur(mail, entrees, sorties);

        c.traiter(EvenementDomaine.builder().type(TypesEvenement.PIECE_CREEE).agregatType("BE").agregatId("be-1").build());
        c.traiter(EvenementDomaine.builder().type(TypesEvenement.PIECE_CREEE).agregatType("BS").agregatId("bs-1").build());

        verify(mail).notifierCreationEntree(be);
        verify(mail).notifierCreationSortie(bs);
        assertThat(c.effetsExternes()).isTrue();
    }
}
