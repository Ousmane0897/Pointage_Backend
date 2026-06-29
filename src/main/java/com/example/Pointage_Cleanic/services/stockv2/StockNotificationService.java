package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.NotificationStockDto;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Publication des notifications de validation stock (7.4) :
 * - broadcast sur {@code /topic/stock-validations} (toute soumission + décision) ;
 * - ciblé sur {@code /user/queue/notifications-stock} (validateur « Responsable Achats », superviseur).
 * Toute défaillance WebSocket est loggée sans casser la transaction métier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockNotificationService {

    private static final String TOPIC = "/topic/stock-validations";
    private static final String QUEUE = "/queue/notifications-stock";

    private final SimpMessagingTemplate messagingTemplate;
    private final ReferentielEmployeStockService referentielEmploye;

    /** Diffuse en broadcast + cible le Responsable Achats (sur son email/principal). */
    public void diffuserEtCiblerResponsableAchats(NotificationStockDto dto) {
        diffuser(dto);
        Optional<DossierEmploye> responsable = safeResponsableAchats();
        responsable.map(DossierEmploye::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .ifPresent(email -> notifier(email, dto));
    }

    public void diffuser(NotificationStockDto dto) {
        try {
            messagingTemplate.convertAndSend(TOPIC, dto);
        } catch (Exception e) {
            log.warn("Diffusion stock-validations échouée : {}", e.getMessage());
        }
    }

    public void notifier(String destinataireEmail, NotificationStockDto dto) {
        if (destinataireEmail == null || destinataireEmail.isBlank()) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(destinataireEmail, QUEUE, dto);
        } catch (Exception e) {
            log.warn("Notification stock ciblée échouée ({}) : {}", destinataireEmail, e.getMessage());
        }
    }

    private Optional<DossierEmploye> safeResponsableAchats() {
        try {
            return referentielEmploye.responsableAchats();
        } catch (Exception e) {
            log.warn("Recherche Responsable Achats échouée : {}", e.getMessage());
            return Optional.empty();
        }
    }
}
