package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Authentifie les sessions STOMP : au {@code CONNECT}, le jeton {@code Authorization: Bearer …}
 * (en-tête STOMP natif) est validé et l'utilisateur devient le principal de la session.
 *
 * <p>Sans jeton valide, le {@code CONNECT} est refusé (trame ERROR). Avec, les envois ciblés
 * {@code convertAndSendToUser(email, …)} atteignent enfin leur destinataire : le nom du
 * principal est l'e-mail, comme pour les requêtes HTTP.
 */
@Component
@RequiredArgsConstructor
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final MyUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        String entete = accessor.getFirstNativeHeader("Authorization");
        if (entete == null || !entete.startsWith("Bearer ")) {
            throw new AccessDeniedException("Connexion WebSocket refusée : jeton absent.");
        }
        try {
            String jeton = entete.substring(7);
            String email = jwtUtil.extractUsername(jeton);
            UserDetails utilisateur = userDetailsService.loadUserByUsername(email);
            if (!jwtUtil.validateToken(jeton, utilisateur)) {
                throw new AccessDeniedException("Connexion WebSocket refusée : jeton invalide.");
            }
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    utilisateur, null, utilisateur.getAuthorities()));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            // Jeton expiré, mal formé, compte supprimé ou désactivé.
            throw new AccessDeniedException("Connexion WebSocket refusée : jeton invalide.");
        }
        return message;
    }
}
