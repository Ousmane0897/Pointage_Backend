package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Notification par e-mail à chaque transition du circuit de validation des congés.
 *
 * <p>Chaque niveau reçoit un message l'invitant à traiter la demande, avec un lien direct
 * vers la fiche. Matrice des destinataires :
 * <table>
 *   <tr><th>Transition</th><th>Destinataire (action attendue)</th><th>En copie</th></tr>
 *   <tr><td>Création</td><td>supérieur hiérarchique (ou RH s'il n'y en a pas)</td><td>demandeur</td></tr>
 *   <tr><td>Validation N1</td><td>comptes RH</td><td>demandeur, supérieur</td></tr>
 *   <tr><td>Validation N2</td><td>comptes super-admin (Direction)</td><td>demandeur, supérieur, RH</td></tr>
 *   <tr><td>Validation N3</td><td>demandeur</td><td>supérieur, RH, Direction</td></tr>
 *   <tr><td>Refus</td><td>demandeur (motif inclus)</td><td>validateurs déjà passés</td></tr>
 *   <tr><td>Annulation</td><td>validateur du niveau courant</td><td>RH</td></tr>
 * </table>
 *
 * <p>Comme pour les bons de stock : <b>un envoi par destinataire</b> (les adresses ne sont pas
 * exposées entre elles), et <b>tout échec SMTP est capté et loggé</b> — un incident e-mail ne
 * doit jamais empêcher une transition d'aboutir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CongeMailNotificationService {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmailService emailService;
    private final CongeDestinataireService destinataires;

    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl = "";

    // ─── Transitions ──────────────────────────────────────────────────────────

    public void notifierCreation(DemandeConge demande) {
        boolean sansSuperieur = Boolean.TRUE.equals(demande.getNiveauSuperieurIgnore());
        Set<String> action = sansSuperieur
                ? destinataires.rh()
                : destinataires.optionnel(destinataires.superieur(demande));
        String complement = sansSuperieur
                ? "Cet employé n'a pas de supérieur hiérarchique renseigné : la demande vous "
                  + "est adressée directement."
                : "";

        envoyer(demande, action,
                "Nouvelle demande de congé à valider",
                "Une demande de congé vient d'être déposée et attend votre validation.",
                complement);

        envoyer(demande, destinataires.optionnel(destinataires.demandeur(demande)),
                "Votre demande de congé a été enregistrée",
                "Votre demande suit désormais le circuit de validation.", "");
    }

    public void notifierValidation(DemandeConge demande, NiveauValidationConge niveauFranchi) {
        NiveauValidationConge suivant = NiveauValidationConge
                .depuisStatut(demande.getStatut()).orElse(null);

        if (suivant == null) {
            // Approbation finale : c'est le demandeur qui est concerné.
            envoyer(demande, destinataires.optionnel(destinataires.demandeur(demande)),
                    "Votre demande de congé est approuvée",
                    "Votre demande a franchi les trois niveaux de validation.", "");
            envoyer(demande, destinataires.fusionner(
                            destinataires.optionnel(destinataires.superieur(demande)),
                            destinataires.rh()),
                    "Demande de congé approuvée",
                    "La demande de %s est définitivement approuvée.".formatted(nomComplet(demande)), "");
            return;
        }

        envoyer(demande, destinataires.validateursDuNiveau(demande, suivant),
                "Demande de congé à valider",
                "La demande a été validée au niveau %s et attend votre décision."
                        .formatted(libelle(niveauFranchi)), "");

        envoyer(demande, destinataires.optionnel(destinataires.demandeur(demande)),
                "Votre demande de congé avance",
                "Votre demande a été validée au niveau %s.".formatted(libelle(niveauFranchi)), "");
    }

    public void notifierRefus(DemandeConge demande, NiveauValidationConge niveau, String motif) {
        envoyer(demande, destinataires.optionnel(destinataires.demandeur(demande)),
                "Votre demande de congé a été refusée",
                "Votre demande a été refusée au niveau %s.".formatted(libelle(niveau)),
                "Motif : " + valeur(motif));

        envoyer(demande, destinataires.validateursDejaPasses(demande),
                "Demande de congé refusée",
                "La demande de %s a été refusée au niveau %s."
                        .formatted(nomComplet(demande), libelle(niveau)),
                "Motif : " + valeur(motif));
    }

    public void notifierAnnulation(DemandeConge demande, NiveauValidationConge niveauCourant) {
        envoyer(demande, destinataires.fusionner(
                        destinataires.validateursDuNiveau(demande, niveauCourant),
                        destinataires.rh()),
                "Demande de congé annulée",
                "La demande de %s a été annulée : aucune action n'est attendue de votre part."
                        .formatted(nomComplet(demande)), "");
    }

    // ─── Envoi ────────────────────────────────────────────────────────────────

    private void envoyer(DemandeConge demande, Set<String> adresses,
                         String sujet, String introduction, String complement) {
        if (adresses == null || adresses.isEmpty()) {
            return;
        }
        try {
            String corps = corpsHtml(sujet, introduction, complement, demande);
            for (String email : adresses) {
                try {
                    emailService.sendHtmlEmail(email, sujet, corps);
                } catch (Exception e) {
                    log.warn("Envoi du mail congé échoué pour {} : {}", email, e.getMessage());
                }
            }
        } catch (Exception e) {
            // Aucune panne de notification ne doit remonter jusqu'à la transition métier.
            log.warn("Préparation du mail congé échouée (demande {}) : {}",
                    demande.getId(), e.getMessage());
        }
    }

    private String corpsHtml(String titre, String introduction, String complement,
                             DemandeConge demande) {
        StringBuilder tableau = new StringBuilder();
        recapitulatif(demande).forEach((cle, val) -> tableau.append("""
                <tr>
                  <td style="padding:8px 12px; font-size:14px; color:#777; border-bottom:1px solid #eee;">%s</td>
                  <td style="padding:8px 12px; font-size:14px; color:#333; font-weight:bold; border-bottom:1px solid #eee;">%s</td>
                </tr>
                """.formatted(echapper(cle), echapper(val))));

        String lien = lien(demande.getId());
        String bouton = lien.isBlank() ? "" : """
                <p style="text-align:center; margin:28px 0;">
                  <a href="%s" style="background:#1B74E4; color:white; padding:12px 24px; border-radius:6px;
                     text-decoration:none; font-size:15px; font-weight:bold;">Ouvrir la demande</a>
                </p>
                """.formatted(lien);

        String bloqueComplement = complement == null || complement.isBlank() ? "" : """
                <p style="font-size:14px; color:#333; background:#fff6e5; border-left:4px solid #f0ad4e;
                          padding:10px 14px; margin:16px 0;">%s</p>
                """.formatted(echapper(complement));

        return """
                <html>
                <body style="margin:0; padding:0; background:#f4f6f8;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f6f8; padding:24px 0;">
                <tr><td align="center">
                <table width="600" cellpadding="0" cellspacing="0"
                       style="background:white; border-radius:8px; padding:30px; font-family:Arial, sans-serif;">
                <tr><td>

                <h2 style="color:#1B74E4; margin-top:0;">%s</h2>

                <p style="font-size:15px; color:#333;">
                Bonjour,<br><br>
                %s
                </p>

                %s

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
                """.formatted(echapper(titre), echapper(introduction), bloqueComplement,
                tableau.toString(), bouton);
    }

    private Map<String, String> recapitulatif(DemandeConge d) {
        Map<String, String> recap = new LinkedHashMap<>();
        recap.put("Employé", nomComplet(d));
        recap.put("Matricule", valeur(d.getMatricule()));
        recap.put("Département", valeur(d.getDepartement()));
        recap.put("Type de congé", libelleEnum(d.getType() == null ? null : d.getType().name()));
        recap.put("Période", "%s → %s".formatted(date(d.getDateDebut()), date(d.getDateFin())));
        recap.put("Nombre de jours ouvrés", d.getNombreJours() == null ? "—" : String.valueOf(d.getNombreJours()));
        recap.put("Motif de la demande", valeur(d.getMotif()));
        recap.put("Statut", libelleEnum(d.getStatut() == null ? null : d.getStatut().name()));

        if (d.getDecisionSuperieur() != null) {
            recap.put("Validé par le supérieur", decision(d.getDecisionSuperieur().getDecideurNom(),
                    d.getDecisionSuperieur().getCommentaire()));
        }
        if (d.getDecisionRh() != null) {
            recap.put("Validé par la RH", decision(d.getDecisionRh().getDecideurNom(),
                    d.getDecisionRh().getCommentaire()));
        }
        if (d.getDecisionDg() != null) {
            recap.put("Validé par la Direction", decision(d.getDecisionDg().getDecideurNom(),
                    d.getDecisionDg().getCommentaire()));
        }
        return recap;
    }

    /**
     * Lien vers la fiche de la demande, ou chaîne vide si l'URL du front n'est pas configurée.
     *
     * <p>⚠ La route {@code /admin/rh/temps-et-presences/conges/demandes/{id}} est un contrat
     * gelé avec le frontend : la changer casse les liens des mails déjà envoyés.
     */
    private String lien(String demandeId) {
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()
                || demandeId == null || demandeId.isBlank()) {
            return "";
        }
        String base = frontendBaseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/admin/rh/temps-et-presences/conges/demandes/" + demandeId;
    }

    // ─── Mise en forme ────────────────────────────────────────────────────────

    private static String decision(String auteur, String commentaire) {
        String base = valeur(auteur);
        return commentaire == null || commentaire.isBlank()
                ? base
                : "%s — « %s »".formatted(base, commentaire.trim());
    }

    private static String nomComplet(DemandeConge d) {
        String complet = "%s %s".formatted(
                d.getPrenom() == null ? "" : d.getPrenom(),
                d.getNom() == null ? "" : d.getNom()).trim();
        return complet.isBlank() ? valeur(d.getEmployeId()) : complet;
    }

    private static String date(java.time.LocalDate d) {
        return d == null ? "—" : d.format(DATE_FR);
    }

    private static String valeur(String v) {
        return v == null || v.isBlank() ? "—" : v.trim();
    }

    /** ENUM_EN_MAJUSCULES → « Enum en majuscules ». */
    private static String libelleEnum(String enumName) {
        if (enumName == null || enumName.isBlank()) {
            return "—";
        }
        String texte = enumName.replace('_', ' ').toLowerCase(Locale.FRENCH);
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1);
    }

    private static String libelle(NiveauValidationConge niveau) {
        if (niveau == null) {
            return "—";
        }
        return switch (niveau) {
            case SUPERIEUR -> "supérieur hiérarchique";
            case RH -> "Ressources humaines";
            case DIRECTION_GENERALE -> "Direction générale";
        };
    }

    private static String echapper(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
