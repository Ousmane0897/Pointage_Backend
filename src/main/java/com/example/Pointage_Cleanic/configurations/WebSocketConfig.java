package com.example.Pointage_Cleanic.configurations;


import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;


/**
 * Dans un projet Spring Boot + WebSocket (avec STOMP), l’URL /ws est simplement le endpoint WebSocket que
 * tu exposes côté backend pour permettre à Angular (ou tout autre client) de se connecter au broker.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Préfixe des topics que les clients peuvent s'abonner
        config.enableSimpleBroker("/topic"); // super admin écoutera ici, Canaux de diffusion. messages envoyés au client qui est le super admin
        config.setApplicationDestinationPrefixes("/app"); // admin enverra ici,Préfixe des requêtes. messages venant du client qui est admin vers le serveur.
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) { // l’URL /ws est simplement le endpoint WebSocket que tu exposes côté backend pour permettre à Angular (ou tout autre client) de se connecter au broker
        registry.addEndpoint("/ws").setAllowedOrigins("http://localhost", "http://localhost:80").withSockJS();
    }

    /**
     * config.enableSimpleBroker("/topic")
     * Rôle : Active un broker simple en mémoire pour diffuser des messages aux clients abonnés.
     * Ce que ça fait : Tous les messages envoyés à un canal commençant par /topic seront automatiquement transmis à tous les clients abonnés à ce canal.
     * Usage typique : Pour les notifications, messages broadcast, ou topics de discussion.
     * Exemple :
     * messagingTemplate.convertAndSend("/topic/cancel-requests", request);
     * → Tous les clients abonnés à /topic/cancel-requests reçoivent le message.
     * ⚠️ Ce broker est “simple”, en mémoire, ne persiste pas les messages et n’est pas fait pour du scaling massif (si tu veux du scaling, il faudra un broker comme RabbitMQ ou ActiveMQ).
     */

    /**
     * 2️⃣ config.setApplicationDestinationPrefixes("/app")
     * Rôle : Définit un préfixe pour les messages envoyés par les clients vers le serveur.
     * Ce que ça fait : Quand un client Angular envoie un message à /app/cancel/request, Spring Boot sait que ça doit aller vers un @MessageMapping côté serveur.
     * Usage typique : Tous les messages destinés à être traités par un contrôleur Spring doivent commencer par ce préfixe.
     * Exemple :
     * this.stompClient.publish({
     *   destination: '/app/cancel/request',
     *   body: JSON.stringify(request)
     * });

     * → Le contrôleur avec @MessageMapping("/cancel/request") reçoit le message.
     */

    /**
     *💡 En pratique :
     * Quand l’admin envoie une demande, il utilise /app/cancel/request → serveur reçoit → serveur envoie via /topic/cancel-requests → super admin reçoit.
     * Quand le super admin répond, il utilise /app/cancel/response → serveur reçoit → serveur envoie via /topic/cancel-responses → admin reçoit.
     */
}
