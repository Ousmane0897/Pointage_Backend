package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.ResetPasswordToken;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.ResetPasswordTokenRepository;
import com.example.Pointage_Cleanic.repositories.UserRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C6 — réinitialisation du mot de passe : SecureRandom, e-mail + code, 5 essais, pas d'énumération. */
@ExtendWith(MockitoExtension.class)
class ResetPasswordServiceTest {

    private static final String EMAIL = "agent@test.com";

    @Mock private ResetPasswordTokenRepository tokenRepo;
    @Mock private UserRepository userRepo;
    @Mock private UtilisateurRepository utilisateurRepo;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ResetPasswordService service;

    private ResetPasswordToken jeton(int tentatives, LocalDateTime expiration) {
        ResetPasswordToken t = new ResetPasswordToken();
        t.setEmail(EMAIL);
        t.setToken("HASH");
        t.setTentatives(tentatives);
        t.setExpiration(expiration);
        return t;
    }

    // --- Demande de code ---

    @Test
    void code_a_six_chiffres_aleatoire() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String c = ResetPasswordService.genererCode();
            assertThat(c).matches("\\d{6}");
            codes.add(c);
        }
        assertThat(codes.size()).isGreaterThan(190);
    }

    @Test
    void demande_compte_existant_code_hache_anciens_codes_supprimes_mail_envoye() throws Exception {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(new User()));
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");

        service.sendResetPasswordEmail("  Agent@Test.com ");

        verify(tokenRepo).deleteByEmail(EMAIL);
        ArgumentCaptor<ResetPasswordToken> captor = ArgumentCaptor.forClass(ResetPasswordToken.class);
        verify(tokenRepo).save(captor.capture());
        assertThat(captor.getValue().getToken()).isEqualTo("HASH");
        assertThat(captor.getValue().getTentatives()).isZero();
        verify(emailService).sendHtmlEmail(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void demande_compte_utilisateur_seul() throws Exception {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(utilisateurRepo.findByEmail(EMAIL)).thenReturn(Optional.of(new Utilisateur()));
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");

        service.sendResetPasswordEmail(EMAIL);

        verify(emailService).sendHtmlEmail(eq(EMAIL), anyString(), anyString());
    }

    @Test
    void demande_email_inconnu_silencieuse() throws Exception {
        when(userRepo.findByEmail("x@test.com")).thenReturn(Optional.empty());
        when(utilisateurRepo.findByEmail("x@test.com")).thenReturn(Optional.empty());

        service.sendResetPasswordEmail("x@test.com"); // aucune exception : pas d'énumération

        verify(tokenRepo, never()).save(any());
        verify(emailService, never()).sendHtmlEmail(any(), any(), any());
    }

    @Test
    void echec_smtp_silencieux() throws Exception {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(new User()));
        when(passwordEncoder.encode(anyString())).thenReturn("HASH");
        doThrow(new RuntimeException("smtp")).when(emailService).sendHtmlEmail(any(), any(), any());

        service.sendResetPasswordEmail(EMAIL); // pas d'exception
    }

    // --- Validation ---

    @Test
    void reset_succes_met_a_jour_les_deux_comptes_et_supprime_le_code() {
        when(tokenRepo.findFirstByEmailOrderByExpirationDesc(EMAIL))
                .thenReturn(Optional.of(jeton(0, LocalDateTime.now().plusMinutes(5))));
        when(passwordEncoder.matches("123456", "HASH")).thenReturn(true);
        when(passwordEncoder.encode("nouveau1")).thenReturn("ENCODED");
        User user = new User();
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        Utilisateur util = new Utilisateur();
        when(utilisateurRepo.findByEmail(EMAIL)).thenReturn(Optional.of(util));

        service.resetPassword(EMAIL, "123456", "nouveau1");

        assertThat(user.getPassword()).isEqualTo("ENCODED");
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(util.getPassword()).isEqualTo("ENCODED");
        verify(tokenRepo).deleteByEmail(EMAIL);
    }

    @Test
    void mauvais_code_incremente_les_tentatives() {
        ResetPasswordToken t = jeton(2, LocalDateTime.now().plusMinutes(5));
        when(tokenRepo.findFirstByEmailOrderByExpirationDesc(EMAIL)).thenReturn(Optional.of(t));
        when(passwordEncoder.matches("000000", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.resetPassword(EMAIL, "000000", "nouveau1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ResetPasswordService.CODE_INVALIDE);
        assertThat(t.getTentatives()).isEqualTo(3);
        verify(tokenRepo).save(t);
        verify(userRepo, never()).save(any());
    }

    @Test
    void cinquieme_echec_invalide_le_code() {
        ResetPasswordToken t = jeton(4, LocalDateTime.now().plusMinutes(5));
        when(tokenRepo.findFirstByEmailOrderByExpirationDesc(EMAIL)).thenReturn(Optional.of(t));
        when(passwordEncoder.matches("000000", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.resetPassword(EMAIL, "000000", "nouveau1"))
                .hasMessage(ResetPasswordService.CODE_INVALIDE);
        verify(tokenRepo).delete(t);
    }

    @Test
    void code_expire_meme_message_et_supprime() {
        ResetPasswordToken t = jeton(0, LocalDateTime.now().minusMinutes(1));
        when(tokenRepo.findFirstByEmailOrderByExpirationDesc(EMAIL)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", "nouveau1"))
                .hasMessage(ResetPasswordService.CODE_INVALIDE);
        verify(tokenRepo).delete(t);
    }

    /** Le code d'un autre compte ne sert à rien : la recherche est faite par e-mail. */
    @Test
    void aucun_code_pour_cet_email_meme_message() {
        when(tokenRepo.findFirstByEmailOrderByExpirationDesc("autre@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("autre@test.com", "123456", "nouveau1"))
                .hasMessage(ResetPasswordService.CODE_INVALIDE);
    }

    @Test
    void mot_de_passe_trop_court_refuse() {
        assertThatThrownBy(() -> service.resetPassword(EMAIL, "123456", "abc"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(tokenRepo, never()).findFirstByEmailOrderByExpirationDesc(any());
    }

    @Test
    void purge_des_codes_expires() {
        service.purgerCodesExpires();
        verify(tokenRepo).deleteByExpirationBefore(any(LocalDateTime.class));
    }
}
