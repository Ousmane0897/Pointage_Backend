package com.example.Pointage_Cleanic.services.pointage;

import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Jeton de QR code d'une agence ({@link SiteClient}).
 * <p>
 * Contenu logique du QR : {@code POINTIC:AG:<qrToken>}. Le QR imprimé encode en réalité
 * une <b>URL</b> du front ({@code <base-url>/home?qr=<payload encodé>}) pour que l'appareil
 * photo natif ouvre l'application ; le front renvoie le payload tel quel au scan, et c'est
 * lui que le serveur valide.
 * <p>
 * ⚠ Jamais l'id Mongo dans le QR : un id est stable et devinable, le jeton est aléatoire
 * et révocable par {@link #regenerer}.
 */
@Service
@RequiredArgsConstructor
public class AgenceQrService {

    public static final String PREFIXE_PAYLOAD = "POINTIC:AG:";

    private static final int OCTETS_TOKEN = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SiteClientRepository siteClientRepository;
    private final Clock clock;

    /** URL publique du front (cf. liens des e-mails). Vide ⇒ le QR porte le payload brut. */
    @Value("${app.frontend.base-url:}")
    private String frontendBaseUrl;

    /** 24 octets {@link SecureRandom}, Base64 URL-safe sans padding (32 caractères). */
    public static String genererToken() {
        byte[] octets = new byte[OCTETS_TOKEN];
        RANDOM.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /**
     * Pose un premier jeton (version 1) si le site n'en a pas. Ne sauvegarde pas :
     * l'appelant persiste l'entité. Renvoie {@code true} si le site a été modifié.
     */
    public boolean assurerToken(SiteClient site) {
        if (site.getQrToken() != null && !site.getQrToken().isBlank()) {
            return false;
        }
        site.setQrToken(genererToken());
        site.setQrVersion(1);
        site.setQrGenereLe(LocalDateTime.now(clock));
        return true;
    }

    /**
     * Nouveau jeton : l'ancien est <b>immédiatement</b> invalide (le scan résout l'agence
     * par jeton, un jeton remplacé n'est plus trouvé). Un pointage déjà ouvert n'en est
     * pas affecté — sa sortie se fera avec le nouveau QR.
     */
    public SiteClient regenerer(String siteId) {
        SiteClient site = siteClientRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Agence introuvable : " + siteId));
        site.setQrToken(genererToken());
        site.setQrVersion(site.getQrVersion() == null ? 1 : site.getQrVersion() + 1);
        site.setQrGenereLe(LocalDateTime.now(clock));
        return siteClientRepository.save(site);
    }

    /** Résout l'agence désignée par un payload complet {@code POINTIC:AG:<token>}. */
    public Optional<SiteClient> resoudre(String payload) {
        String token = extraireToken(payload);
        return token == null ? Optional.empty() : siteClientRepository.findByQrToken(token);
    }

    /** Jeton contenu dans le payload, ou {@code null} si le préfixe est absent ou le jeton vide. */
    public static String extraireToken(String payload) {
        if (payload == null) {
            return null;
        }
        String p = payload.trim();
        if (!p.startsWith(PREFIXE_PAYLOAD)) {
            return null;
        }
        String token = p.substring(PREFIXE_PAYLOAD.length());
        return token.isBlank() ? null : token;
    }

    public static boolean prefixeReconnu(String payload) {
        return payload != null && payload.trim().startsWith(PREFIXE_PAYLOAD);
    }

    public String payload(SiteClient site) {
        return site.getQrToken() == null ? null : PREFIXE_PAYLOAD + site.getQrToken();
    }

    /** Contenu effectivement imprimé dans le QR. */
    public String url(SiteClient site) {
        String payload = payload(site);
        if (payload == null) {
            return null;
        }
        if (frontendBaseUrl == null || frontendBaseUrl.isBlank()) {
            return payload;
        }
        String base = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
        return base + "/home?qr=" + URLEncoder.encode(payload, StandardCharsets.UTF_8);
    }
}
