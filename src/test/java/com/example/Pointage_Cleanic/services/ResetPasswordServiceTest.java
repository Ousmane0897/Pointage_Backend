package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.ResetPasswordToken;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.ResetPasswordTokenRepository;
import com.example.Pointage_Cleanic.repositories.UserRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ResetPasswordServiceTest {

    @Mock
    private ResetPasswordTokenRepository tokenRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private UtilisateurRepository utilisateurRepo;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ResetPasswordService service;

    // ---------------------------------------------------------------------
    // 1️⃣ TEST sendResetPasswordEmail() → pour un SUPER ADMIN (User)
    // ---------------------------------------------------------------------
    @Test
    void testSendResetPasswordEmail_UserAccount() throws MessagingException {

        String email = "admin@test.com";

        User user = new User();
        user.setEmail(email);

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(tokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sendResetPasswordEmail(email);

        verify(userRepo).findByEmail(email);
        verify(utilisateurRepo, never()).findByEmail(any());

        verify(tokenRepo).save(any());
        verify(emailService).sendHtmlEmail(eq(email), anyString(), anyString());
    }



    // ---------------------------------------------------------------------
    // 2️⃣ TEST sendResetPasswordEmail() → pour un ADMIN (Utilisateur)
    // ---------------------------------------------------------------------
    @Test
    void testSendResetPasswordEmail_AdminAccount() throws MessagingException {

        String email = "admin2@test.com";

        Utilisateur util = new Utilisateur();
        util.setEmail(email);

        when(userRepo.findByEmail(email)).thenReturn(Optional.empty());
        when(utilisateurRepo.findByEmail(email)).thenReturn(Optional.of(util));
        when(tokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.sendResetPasswordEmail(email);

        verify(userRepo).findByEmail(email);
        verify(utilisateurRepo).findByEmail(email);

        verify(tokenRepo).save(any());
        verify(emailService).sendHtmlEmail(eq(email), anyString(), anyString());
    }


    // ---------------------------------------------------------------------
    // 3️⃣ TEST sendResetPasswordEmail() → email inconnu
    // ---------------------------------------------------------------------
    @Test
    void testSendResetPasswordEmail_UnknownEmail() {

        when(userRepo.findByEmail("x@test.com")).thenReturn(Optional.empty());
        when(utilisateurRepo.findByEmail("x@test.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            service.sendResetPasswordEmail("x@test.com");
        });
    }

    // ---------------------------------------------------------------------
    // 4️⃣ TEST resetPassword() → reset OK
    // ---------------------------------------------------------------------
    @Test
    void testResetPassword_Success() {

        String email = "admin@test.com";

        ResetPasswordToken token = new ResetPasswordToken();
        token.setEmail(email);
        token.setToken("123456");
        token.setExpiration(LocalDateTime.now().plusMinutes(5));

        User user = new User();
        user.setEmail(email);

        Map<String, String> req = new HashMap<>();
        req.put("code", "123456");
        req.put("newPassword", "newPass");

        when(tokenRepo.findByToken("123456")).thenReturn(Optional.of(token));

        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass")).thenReturn("ENCODED");

        // utilisateur optionnel
        Utilisateur util = new Utilisateur();
        util.setEmail(email);
        when(utilisateurRepo.findByEmail(email)).thenReturn(Optional.of(util));

        service.resetPassword(req);

        // --- VÉRIFICATIONS CLÉS ---
        assertEquals("ENCODED", user.getPassword());
        assertFalse(user.isMustChangePassword());

        assertEquals("ENCODED", util.getPassword());
        assertFalse(util.isMustChangePassword());

        verify(tokenRepo).delete(token);
        verify(userRepo).save(user);
        verify(utilisateurRepo).save(util);
    }

    // ---------------------------------------------------------------------
    // 5️⃣ TEST resetPassword() → token expiré
    // ---------------------------------------------------------------------
    @Test
    void testResetPassword_TokenExpired() {

        ResetPasswordToken token = new ResetPasswordToken();
        token.setToken("111111");
        token.setExpiration(LocalDateTime.now().minusMinutes(1));

        when(tokenRepo.findByToken("111111")).thenReturn(Optional.of(token));

        Map<String, String> req = Map.of(
                "code", "111111",
                "newPassword", "1234"
        );

        assertThrows(RuntimeException.class, () -> service.resetPassword(req));
    }

    // ---------------------------------------------------------------------
    // 6️⃣ TEST resetPassword() → token invalide
    // ---------------------------------------------------------------------
    @Test
    void testResetPassword_InvalidToken() {

        when(tokenRepo.findByToken("badcode")).thenReturn(Optional.empty());

        Map<String, String> req = Map.of(
                "code", "badcode",
                "newPassword", "1234"
        );

        assertThrows(RuntimeException.class, () -> service.resetPassword(req));
    }
}
