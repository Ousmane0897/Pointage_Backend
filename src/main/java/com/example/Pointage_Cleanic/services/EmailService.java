package com.example.Pointage_Cleanic.services;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * Expéditeur des messages : le compte SMTP configuré ({@code spring.mail.username}).
     * La valeur d'initialisation sert de repli hors contexte Spring et en profil par défaut,
     * où {@code spring.mail.username} n'est pas renseigné.
     */
    @Value("${spring.mail.username:cleanicsarl24@gmail.com}")
    private String expediteur = "cleanicsarl24@gmail.com";

    public void sendSimpleEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        message.setFrom(expediteur);
        mailSender.send(message);
    }

    public void sendHtmlEmail(String to, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, "utf-8");

        helper.setText(html, true); // true = interpréter comme HTML
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setFrom(expediteur);

        mailSender.send(message);
    }

}
