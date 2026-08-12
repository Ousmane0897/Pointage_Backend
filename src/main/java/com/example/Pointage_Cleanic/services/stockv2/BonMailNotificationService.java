package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.Enum.stockv2.SensBon;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.DestinataireBon;
import com.example.Pointage_Cleanic.entities.stockv2.EntreeHistorique;
import com.example.Pointage_Cleanic.repositories.UserRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Notification par e-mail à la création d'un bon d'entrée ou de sortie (7.4).
 * <p>
 * Destinataires : le SUPERADMIN et les CONTROLEUR_STOCK, sur l'adresse qu'ils utilisent pour
 * se connecter. Le rôle {@code SUPERADMIN} n'existe que dans la collection {@code login}
 * ({@link User}) ; {@code CONTROLEUR_STOCK} vient du profil {@link Utilisateur}. Comme
 * l'authentification retrouve le compte {@code login} par e-mail, les deux documents portent
 * bien l'adresse de connexion.
 * <p>
 * L'envoi reprend la mécanique du mail « mot de passe oublié »
 * ({@code ResetPasswordService}) : appel synchrone à {@code EmailService.sendHtmlEmail} avec
 * un corps HTML construit en texte bloc.
 * <p>
 * Comme {@code StockNotificationService}, toute défaillance est loggée sans jamais casser la
 * transaction métier : <b>un incident SMTP ne doit pas empêcher la création du bon</b>.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BonMailNotificationService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";
    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Espace insécable U+00A0, séparateur de milliers fr-FR. */
    private static final String NBSP = " ";

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final UtilisateurRepository utilisateurRepository;

    /** URL publique du front, pour le lien « Ouvrir le bon ». Lien omis si vide. */
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl = "";

    public void notifierCreationSortie(BonSortie bon) {
        if (bon == null) {
            return;
        }
        Map<String, String> lignes = new LinkedHashMap<>();
        lignes.put("Référence", valeur(bon.getReference()));
        lignes.put("Type", libelle(bon.getType() == null ? null : bon.getType().name()));
        lignes.put("Date", date(bon.getDate()));
        lignes.put("Site source", valeur(bon.getSiteSourceNom()));
        lignes.put("Destinataire", destinataire(bon.getDestinataire()));
        lignes.put("Nombre de lignes", String.valueOf(bon.getLignes() == null ? 0 : bon.getLignes().size()));
        lignes.put("Montant total", montant(bon.getMontantTotal()));
        lignes.put("Créé par", auteur(bon.getHistorique()));

        envoyer(SensBon.SORTIE, bon.getId(), bon.getReference(), lignes);
    }

    public void notifierCreationEntree(BonEntree bon) {
        if (bon == null) {
            return;
        }
        Map<String, String> lignes = new LinkedHashMap<>();
        lignes.put("Référence", valeur(bon.getReference()));
        lignes.put("Type", libelle(bon.getType() == null ? null : bon.getType().name()));
        lignes.put("Date", date(bon.getDate()));
        lignes.put("Site destination", valeur(bon.getSiteDestinationNom()));
        lignes.put("Fournisseur", valeur(bon.getFournisseur()));
        lignes.put("Nombre de lignes", String.valueOf(bon.getLignes() == null ? 0 : bon.getLignes().size()));
        lignes.put("Montant total", montant(bon.getMontantTotal()));
        lignes.put("Créé par", auteur(bon.getHistorique()));

        envoyer(SensBon.ENTREE, bon.getId(), bon.getReference(), lignes);
    }

    // ------------------------------------------------------------------
    // Envoi
    // ------------------------------------------------------------------

    private void envoyer(SensBon sens, String bonId, String reference, Map<String, String> recapitulatif) {
        try {
            Set<String> destinataires = destinataires();
            if (destinataires.isEmpty()) {
                log.warn("Aucun destinataire (SUPERADMIN / CONTROLEUR_STOCK) pour la création du bon {}", reference);
                return;
            }
            String intitule = sens == SensBon.ENTREE ? "bon d'entrée" : "bon de sortie";
            String sujet = "Nouveau " + intitule + " " + valeur(reference);
            String corps = corpsHtml(intitule, bonId, sens, recapitulatif);

            // Un envoi par destinataire : EmailService ne gère qu'une adresse, et cela évite
            // d'exposer les adresses des uns aux autres.
            for (String email : destinataires) {
                try {
                    emailService.sendHtmlEmail(email, sujet, corps);
                } catch (Exception e) {
                    log.warn("Envoi du mail de création du bon {} échoué pour {} : {}",
                            reference, email, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Notification e-mail de création du bon {} échouée : {}", reference, e.getMessage());
        }
    }

    /** Adresses de connexion du SUPERADMIN et des CONTROLEUR_STOCK actifs, dédoublonnées. */
    private Set<String> destinataires() {
        Stream<String> superAdmins = userRepository.findByRoleIgnoreCase(ROLE_SUPERADMIN)
                .stream()
                .map(User::getEmail);
        Stream<String> controleurs = utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.CONTROLEUR_STOCK)
                .stream()
                .map(Utilisateur::getEmail);

        Set<String> emails = new LinkedHashSet<>();
        Stream.concat(superAdmins, controleurs)
                .filter(email -> email != null && !email.isBlank())
                .map(email -> email.trim().toLowerCase(Locale.ROOT))
                .forEach(emails::add);
        return emails;
    }

    // ------------------------------------------------------------------
    // Corps HTML — même facture que le mail de réinitialisation de mot de passe
    // ------------------------------------------------------------------

    private String corpsHtml(String intitule, String bonId, SensBon sens, Map<String, String> recapitulatif) {
        StringBuilder tableau = new StringBuilder();
        recapitulatif.forEach((cle, val) -> tableau.append("""
                <tr>
                  <td style="padding:8px 12px; font-size:14px; color:#777; border-bottom:1px solid #eee;">%s</td>
                  <td style="padding:8px 12px; font-size:14px; color:#333; font-weight:bold; border-bottom:1px solid #eee;">%s</td>
                </tr>
                """.formatted(echapper(cle), echapper(val))));

        String lien = lien(bonId, sens);
        String bouton = lien.isBlank() ? "" : """
                <p style="text-align:center; margin:28px 0;">
                  <a href="%s" style="background:#1B74E4; color:white; padding:12px 24px; border-radius:6px;
                     text-decoration:none; font-size:15px; font-weight:bold;">Ouvrir le bon</a>
                </p>
                """.formatted(lien);

        return """
                <html>
                <body style="margin:0; padding:0; background:#f4f6f8;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8; padding:24px 0;">
                <tr><td align="center">
                <table width="600" cellpadding="0" cellspacing="0"
                       style="background:white; border-radius:8px; padding:30px; font-family:Arial, sans-serif;">
                <tr><td>

                <h2 style="color:#1B74E4; margin-top:0;">Nouveau %s</h2>

                <p style="font-size:15px; color:#333;">
                Bonjour,<br><br>
                Un %s vient d'être créé dans PointIC. Il est à l'état <b>brouillon</b> et attend d'être traité.
                </p>

                <table width="100%%" cellpadding="0" cellspacing="0" style="margin:20px 0; border-collapse:collapse;">
                %s
                </table>

                %s

                <p style="font-size:14px; color:#333;">Cordialement,</p>
                <p style="font-size:15px; font-weight:bold; color:#1B74E4;">
                L'équipe Technique Cleanic Sénégal
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
                </td></tr>
                </table>
                </body>
                </html>
                """.formatted(intitule, intitule, tableau.toString(), bouton);
    }

    /** Lien vers la fiche du bon, ou chaîne vide si l'URL du front n'est pas configurée. */
    private String lien(String bonId, SensBon sens) {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank() || bonId == null || bonId.isBlank()) {
            return "";
        }
        String base = frontendBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String segment = sens == SensBon.ENTREE ? "bons-entree" : "bons-sortie";
        return base + "/admin/stock-v2/controle-mouvements/" + segment + "/" + bonId;
    }

    // ------------------------------------------------------------------
    // Mise en forme
    // ------------------------------------------------------------------

    private String valeur(String v) {
        return v == null || v.isBlank() ? "—" : v.trim();
    }

    /** ENUM_EN_MAJUSCULES -> « Enum en majuscules ». */
    private String libelle(String enumName) {
        if (enumName == null || enumName.isBlank()) {
            return "—";
        }
        String texte = enumName.replace('_', ' ').toLowerCase(Locale.FRENCH);
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }

    private String date(LocalDate d) {
        return d == null ? "—" : d.format(DATE_FR);
    }

    /** Montant en FCFA, sans décimale, avec séparateur de milliers insécable (fr-FR). */
    private String montant(long montant) {
        String brut = String.valueOf(Math.abs(montant));
        List<String> tranches = new ArrayList<>();
        for (int fin = brut.length(); fin > 0; fin -= 3) {
            tranches.add(0, brut.substring(Math.max(0, fin - 3), fin));
        }
        // Espace insécable : typographie fr-FR, et pas de coupure de ligne au milieu
        // du montant dans le rendu du client mail.
        return (montant < 0 ? "-" : "") + String.join(NBSP, tranches) + NBSP + "FCFA";
    }

    private String destinataire(DestinataireBon d) {
        if (d == null) {
            return "—";
        }
        if (d.getSiteNom() != null && !d.getSiteNom().isBlank()) {
            return d.getSiteNom();
        }
        if (d.getAgentNom() != null && !d.getAgentNom().isBlank()) {
            return d.getAgentNom();
        }
        return valeur(d.getClientNom());
    }

    private String auteur(List<EntreeHistorique> historique) {
        if (historique == null) {
            return "—";
        }
        return historique.stream()
                .filter(h -> h.getAction() == ActionWorkflow.CREATION)
                .map(EntreeHistorique::getAuteur)
                .filter(a -> a != null && !a.isBlank())
                .findFirst()
                .orElse("—");
    }

    /** Neutralise le HTML des valeurs métier (nom de client, fournisseur… saisis librement). */
    private String echapper(String v) {
        return v == null ? "" : v
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
