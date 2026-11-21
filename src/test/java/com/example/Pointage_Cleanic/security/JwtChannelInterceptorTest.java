package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** C6 — authentification des sessions STOMP au CONNECT. */
class JwtChannelInterceptorTest {

    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final MyUserDetailsService users = mock(MyUserDetailsService.class);
    private final JwtChannelInterceptor intercepteur = new JwtChannelInterceptor(jwtUtil, users);
    private final MessageChannel canal = mock(MessageChannel.class);

    private static Message<byte[]> trame(StompCommand commande, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(commande);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connect_avec_jeton_valide_pose_le_principal_email() {
        var details = new User("agent@cleanic.sn", "x", List.of());
        when(jwtUtil.extractUsername("bon")).thenReturn("agent@cleanic.sn");
        when(users.loadUserByUsername("agent@cleanic.sn")).thenReturn(details);
        when(jwtUtil.validateToken("bon", details)).thenReturn(true);

        Message<?> resultat = intercepteur.preSend(trame(StompCommand.CONNECT, "Bearer bon"), canal);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(resultat, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("agent@cleanic.sn");
    }

    @Test
    void connect_sans_jeton_refuse() {
        assertThatThrownBy(() -> intercepteur.preSend(trame(StompCommand.CONNECT, null), canal))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> intercepteur.preSend(trame(StompCommand.CONNECT, "Bearer null"), canal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connect_jeton_invalide_ou_compte_desactive_refuse() {
        when(jwtUtil.extractUsername("faux")).thenThrow(new RuntimeException("signature"));
        assertThatThrownBy(() -> intercepteur.preSend(trame(StompCommand.CONNECT, "Bearer faux"), canal))
                .isInstanceOf(AccessDeniedException.class);

        when(jwtUtil.extractUsername("desactive")).thenReturn("off@cleanic.sn");
        when(users.loadUserByUsername("off@cleanic.sn")).thenThrow(new DisabledException("off"));
        assertThatThrownBy(() -> intercepteur.preSend(trame(StompCommand.CONNECT, "Bearer desactive"), canal))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Les trames suivantes (SUBSCRIBE, SEND…) portent déjà l'utilisateur de la session. */
    @Test
    void autres_trames_inchangees() {
        Message<byte[]> abonnement = trame(StompCommand.SUBSCRIBE, null);
        assertThat(intercepteur.preSend(abonnement, canal)).isSameAs(abonnement);
    }

    @Test
    void origines_websocket_sans_joker_global() {
        assertThat(OriginesAutorisees.MOTIFS).doesNotContain("*").noneMatch(o -> o.contains("*.ngrok"));
    }
}
