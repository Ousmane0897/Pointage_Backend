package com.example.Pointage_Cleanic.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    /** Injecté depuis {@code spring.mail.username} en exécution réelle. */
    private static final String EXPEDITEUR = "cleanicsarl24@gmail.com";

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        // @Value n'est pas résolu hors contexte Spring : on pose la valeur à la main.
        ReflectionTestUtils.setField(emailService, "expediteur", EXPEDITEUR);
    }


    // ---------------------------------------------------------
    // TEST: sendSimpleEmail()
    // ---------------------------------------------------------
    @Test
    void testSendSimpleEmail() {

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendSimpleEmail("test@example.com", "Sujet X", "Hello World");

        verify(mailSender).send(captor.capture());

        SimpleMailMessage sentMessage = captor.getValue();

        assertEquals("test@example.com", sentMessage.getTo()[0]);
        assertEquals("Sujet X", sentMessage.getSubject());
        assertEquals("Hello World", sentMessage.getText());
        // Expéditeur = spring.mail.username, avec repli sur le compte Cleanic hors Spring.
        assertEquals(EXPEDITEUR, sentMessage.getFrom());
    }


    // ---------------------------------------------------------
    // TEST: sendHtmlEmail()
    // ---------------------------------------------------------
    @Test
    void testSendHtmlEmail() throws MessagingException {

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailService.sendHtmlEmail("client@test.com", "Bienvenue", "<h1>Hello</h1>");

        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
    }
}
