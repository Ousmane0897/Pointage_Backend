package com.example.Pointage_Cleanic.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;


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
        assertEquals("tonEmail@gmail.com", sentMessage.getFrom());
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
