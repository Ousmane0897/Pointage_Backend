package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.ResetPasswordToken;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.ResetPasswordTokenRepository;
import com.example.Pointage_Cleanic.repositories.UserRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResetPasswordService {

    private final ResetPasswordTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final UtilisateurRepository utilisateurRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    // 🔍 Trouver un compte (User ou Utilisateur) via email
    private Object findAccountByEmail(String email) {

        Optional<User> userOpt = userRepo.findByEmail(email);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }

        Optional<Utilisateur> utilOpt = utilisateurRepo.findByEmail(email);
        if (utilOpt.isPresent()) {
            return utilOpt.get();
        }

        throw new RuntimeException("Aucun utilisateur trouvé avec cet email");
    }



    // 📩 Envoi du code OTP
    public void sendResetPasswordEmail(String email) throws MessagingException {

        // Normaliser dès l'entrée pour rester cohérent avec resetPassword (lookup en minuscules)
        // et avec la création de compte qui stocke l'email en minuscules.
        email = email.trim().toLowerCase(Locale.ROOT);

        Object account = findAccountByEmail(email);

        // Génération d’un OTP
        String code = String.valueOf((int) (Math.random() * 900000) + 100000);

        ResetPasswordToken token = new ResetPasswordToken();
        token.setEmail(email);
        token.setToken(code);
        token.setExpiration(LocalDateTime.now().plusMinutes(10));
        tokenRepo.save(token);

        // Corps HTML corporate
        String body = """
<html>
<body style="font-family:Arial, sans-serif; padding:20px; background:#f5f7fa;">

<table width="100%%" style="max-width:600px; margin:auto; background:white; padding:30px; border-radius:10px; box-shadow:0 4px 12px rgba(0,0,0,0.08);">
<tr><td>

<h2 style="color:#1B74E4; margin-bottom:10px;">🔐 Sécurité de votre compte – Cleanic Sénégal</h2>

<p style="font-size:15px; color:#333;">
Nous avons reçu une demande de réinitialisation de mot de passe pour votre compte <b>PointIC</b>.
Veuillez utiliser le code ci-dessous pour continuer :
</p>

<div style="background:#1B74E4; color:white; padding:15px; font-size:32px; text-align:center; border-radius:8px; margin:20px 0;">
<b>%s</b>
</div>

<p style="font-size:14px; color:#555;">
⏳ Ce code expire dans <b>10 minutes</b>.
</p>

<br>

<p style="font-size:14px; color:#333;">Cordialement,</p>
<p style="font-size:15px; font-weight:bold; color:#1B74E4;">
L’équipe Technique Cleanic Sénégal
</p>

<hr style="border:none; border-top:1px solid #ddd; margin-top:30px;">

<p style="font-size:12px; color:#777; text-align:center;">
Cleanic Sénégal • Solutions Digitales<br>
Dakar, Sénégal<br>
📧 support@cleanicsenegal.com<br>
🌐 <a href="https://cleanicsenegal.com" style="color:#1B74E4;">cleanicsenegal.com</a>
</p>

</td></tr>
</table>

</body>
</html>
""".formatted(code);



        emailService.sendHtmlEmail(email, "Réinitialisation de votre mot de passe", body);
    }


    // 🔐 Réinitialiser le mot de passe
    // Exemple de snippet (adaptation de ta méthode existante)
    public void resetPassword(Map<String, String> request) {

        String cleanCode = request.get("code").trim();
        String newPassword = request.get("newPassword");

        ResetPasswordToken token = tokenRepo.findByToken(cleanCode)
                .orElseThrow(() -> new RuntimeException("Code invalide"));

        if (token.getExpiration().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Code expiré");
        }

        String email = token.getEmail().trim().toLowerCase(Locale.ROOT);
        String encodedPassword = passwordEncoder.encode(newPassword);

        // Mettre à jour login (source de vérité du mot de passe)
        User loginAccount = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Login introuvable pour cet utilisateur"));

        loginAccount.setPassword(encodedPassword);
        loginAccount.setMustChangePassword(false);
        userRepo.save(loginAccount);

        // Mettre à jour le profil Utilisateur s'il existe (pour garder cohérence)
        utilisateurRepo.findByEmail(email).ifPresent(util -> {
            util.setPassword(encodedPassword);
            util.setMustChangePassword(false);
            utilisateurRepo.save(util);
        });

        tokenRepo.delete(token);
    }


}
