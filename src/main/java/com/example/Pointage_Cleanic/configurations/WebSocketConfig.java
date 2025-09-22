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
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
