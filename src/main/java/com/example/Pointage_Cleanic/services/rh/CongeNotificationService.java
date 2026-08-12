package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.NotificationCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Publication des notifications temps réel du circuit de validation des congés :
 * <ul>
 *   <li>broadcast sur {@code /topic/conges-validations} (toute transition) ;</li>
 *   <li>ciblé sur {@code /user/queue/notifications-conges} — validateurs du niveau désormais
 *       attendu, plus le demandeur.</li>
 * </ul>
 *
 * <p>⚠ Le ciblage se fait par <b>e-mail</b> : c'est le principal du JWT, donc la clé
 * utilisateur de {@code convertAndSendToUser}. Un ciblage par id n'arriverait jamais au front.
 *
 * <p>Toute défaillance WebSocket est loggée sans casser la transaction métier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CongeNotificationService {

    private static final String TOPIC = "/topic/conges-validations";
    private static final String QUEUE = "/queue/notifications-conges";

    private final SimpMessagingTemplate messagingTemplate;
    private final CongeDestinataireService destinataires;

    /**
     * Diffuse la transition et cible les personnes qui doivent agir (validateurs du niveau
     * courant) ainsi que le demandeur, informé de l'avancement de sa demande.
     */
    public void diffuserEtCiblerValidateurs(DemandeConge demande, String type,
                                            String titre, String message) {
        NiveauValidationConge niveau = NiveauValidationConge
                .depuisStatut(demande.getStatut()).orElse(null);

        NotificationCongeDto dto = NotificationCongeDto.builder()
                .type(type)
                .demandeId(demande.getId())
                .employeId(demande.getEmployeId())
                .employeNom(nomComplet(demande))
                .niveau(niveau)
                .statut(demande.getStatut())
                .titre(titre)
                .message(message)
                .dateEmission(LocalDateTime.now())
                .build();

        diffuser(dto);

        Set<String> cibles = destinataires.fusionner(
                destinataires.validateursDuNiveau(demande, niveau),
                destinataires.optionnel(destinataires.demandeur(demande)));
        cibles.forEach(email -> notifier(email, dto));
    }

    public void diffuser(NotificationCongeDto dto) {
        try {
            messagingTemplate.convertAndSend(TOPIC, dto);
        } catch (Exception e) {
            log.warn("Diffusion conges-validations échouée : {}", e.getMessage());
        }
    }

    public void notifier(String destinataireEmail, NotificationCongeDto dto) {
        if (destinataireEmail == null || destinataireEmail.isBlank()) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(destinataireEmail, QUEUE, dto);
        } catch (Exception e) {
            log.warn("Notification congé ciblée échouée ({}) : {}", destinataireEmail, e.getMessage());
        }
    }

    private static String nomComplet(DemandeConge d) {
        String complet = "%s %s".formatted(
                d.getPrenom() == null ? "" : d.getPrenom(),
                d.getNom() == null ? "" : d.getNom()).trim();
        return complet.isBlank() ? d.getEmployeId() : complet;
    }
}
