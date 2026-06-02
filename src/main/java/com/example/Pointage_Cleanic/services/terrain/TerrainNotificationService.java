package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.NotificationTerrain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Diffusion temps réel STOMP du module Terrain.
 * <ul>
 *   <li>{@code /topic/alertes-terrain} — broadcast d'une alerte (création/escalade)</li>
 *   <li>{@code /topic/pointages-terrain} — broadcast d'un pointage (supervision live)</li>
 *   <li>{@code /user/queue/notifications-terrain} — notification ciblée au destinataire courant</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TerrainNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void diffuserAlerte(Object alerteDto) {
        try {
            messagingTemplate.convertAndSend("/topic/alertes-terrain", alerteDto);
        } catch (Exception e) {
            log.warn("Diffusion alerte terrain échouée : {}", e.getMessage());
        }
    }

    public void diffuserPointage(Object pointageDto) {
        try {
            messagingTemplate.convertAndSend("/topic/pointages-terrain", pointageDto);
        } catch (Exception e) {
            log.warn("Diffusion pointage terrain échouée : {}", e.getMessage());
        }
    }

    public void notifierDestinataire(String destinataireId, NotificationTerrain notification) {
        if (destinataireId == null || destinataireId.isBlank()) {
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(destinataireId, "/queue/notifications-terrain", notification);
        } catch (Exception e) {
            log.warn("Notification ciblée terrain échouée ({}) : {}", destinataireId, e.getMessage());
        }
    }
}