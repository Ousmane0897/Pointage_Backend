package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.ResetPasswordToken;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.repositories.ResetPasswordTokenRepository;
import com.example.Pointage_Cleanic.repositories.UserRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Réinitialisation du mot de passe par code envoyé par e-mail (lot 0 sécurité, C6).
 *
 * <ul>
 *   <li>Code à 6 chiffres tiré par {@link SecureRandom}, stocké <b>haché</b> (BCrypt), valable
 *       10 minutes ; une nouvelle demande invalide les codes précédents du même e-mail.</li>
 *   <li>Validation sur <b>e-mail + code</b> ; {@value #MAX_TENTATIVES} essais au maximum, puis le
 *       code est supprimé.</li>
 *   <li>Aucune énumération des comptes : la demande répond pareil que l'e-mail existe ou non, et
 *       toute erreur de validation renvoie le même message.</li>
 *   <li>Codes expirés purgés toutes les heures.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResetPasswordService {

    static final int MAX_TENTATIVES = 5;
    static final int VALIDITE_MINUTES = 10;
    static final String CODE_INVALIDE = "Code invalide ou expiré.";
    private static final SecureRandom ALEA = new SecureRandom();

    private final ResetPasswordTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final UtilisateurRepository utilisateurRepo;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static String normaliser(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Code à 6 chiffres (100000–999999), aléatoire cryptographique. */
    static String genererCode() {
        return String.valueOf(100_000 + ALEA.nextInt(900_000));
    }

    /**
     * Envoie un code si l'e-mail correspond à un compte. Ne lève jamais d'erreur visible de
     * l'appelant (ni compte inconnu, ni échec SMTP) : la réponse HTTP est identique dans tous les cas.
     */
    public void sendResetPasswordEmail(String emailBrut) {
        String email = normaliser(emailBrut);
        if (email.isEmpty()) {
            return;
        }
        boolean existe = userRepo.findByEmail(email).isPresent()
                || utilisateurRepo.findByEmail(email).isPresent();
        if (!existe) {
            return;
        }

        tokenRepo.deleteByEmail(email);
        String code = genererCode();
        ResetPasswordToken token = new ResetPasswordToken();
        token.setEmail(email);
        token.setToken(passwordEncoder.encode(code));
        token.setExpiration(LocalDateTime.now().plusMinutes(VALIDITE_MINUTES));
        token.setTentatives(0);
        tokenRepo.save(token);

        try {
            emailService.sendHtmlEmail(email, "Réinitialisation de votre mot de passe", corpsMail(code));
        } catch (Exception e) {
            // Ne pas trahir l'existence du compte par une erreur : journaliser seulement.
            log.warn("Échec d'envoi du code de réinitialisation : {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Valide le code de l'e-mail donné et remplace le mot de passe.
     *
     * @throws IllegalArgumentException message générique {@value #CODE_INVALIDE}, ou règle de mot de passe
     */
    public void resetPassword(String emailBrut, String codeBrut, String newPassword) {
        String email = normaliser(emailBrut);
        String code = codeBrut == null ? "" : codeBrut.trim();
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins 6 caractères.");
        }

        Optional<ResetPasswordToken> trouve = email.isEmpty()
                ? Optional.empty()
                : tokenRepo.findFirstByEmailOrderByExpirationDesc(email);
        if (trouve.isEmpty()) {
            throw new IllegalArgumentException(CODE_INVALIDE);
        }
        ResetPasswordToken token = trouve.get();
        if (token.getExpiration() == null || token.getExpiration().isBefore(LocalDateTime.now())) {
            tokenRepo.delete(token);
            throw new IllegalArgumentException(CODE_INVALIDE);
        }
        if (code.isEmpty() || !passwordEncoder.matches(code, token.getToken())) {
            int tentatives = token.getTentatives() + 1;
            if (tentatives >= MAX_TENTATIVES) {
                tokenRepo.delete(token);
            } else {
                token.setTentatives(tentatives);
                tokenRepo.save(token);
            }
            throw new IllegalArgumentException(CODE_INVALIDE);
        }

        User loginAccount = userRepo.findByEmail(email).orElse(null);
        if (loginAccount == null) {
            tokenRepo.delete(token);
            throw new IllegalArgumentException(CODE_INVALIDE);
        }
        String encodedPassword = passwordEncoder.encode(newPassword);
        loginAccount.setPassword(encodedPassword);
        loginAccount.setMustChangePassword(false);
        userRepo.save(loginAccount);

        // Profil Utilisateur, s'il existe, gardé cohérent.
        utilisateurRepo.findByEmail(email).ifPresent(util -> {
            util.setPassword(encodedPassword);
            util.setMustChangePassword(false);
            utilisateurRepo.save(util);
        });

        tokenRepo.deleteByEmail(email);
    }

    /** Purge horaire des codes expirés. */
    @Scheduled(fixedRate = 60 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void purgerCodesExpires() {
        tokenRepo.deleteByExpirationBefore(LocalDateTime.now());
    }

    private static String corpsMail(String code) {
        return """
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
    }
}
