package com.example.Pointage_Cleanic.security;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Compteur journalier des appels à une route en sursis (voir {@link SuiviRoutesLegacyFilter}).
 * Un document par couple (route, jour) : {@code _id = "<route>|<yyyy-MM-dd>"}.
 *
 * <p>Ne contient <b>aucune donnée personnelle</b> : ni URL réelle (l'identifiant d'agent de
 * {@code /api/employe-complet/image/{agentId}} est son code de pointage), ni IP, ni jeton.
 */
@Data
@Document("securite_appels_legacy")
public class AppelRouteLegacy {

    @Id
    private String id;

    /** Motif de la route surveillée, ex. {@code GET /api/produits/image/**}. */
    private String route;

    /** Jour de l'appel ({@code yyyy-MM-dd}, fuseau Africa/Dakar). */
    private String jour;

    private long compteur;

    /** Nombre d'appels par code HTTP renvoyé, ex. {@code {"200": 3, "401": 1}}. */
    private Map<String, Long> statuts;

    private LocalDateTime premierAppel;
    private LocalDateTime dernierAppel;

    /** User-Agent du dernier appel (tronqué) : aide à reconnaître le client (navigateur, appli…). */
    private String dernierUserAgent;

    /** En-tête Origin, ou à défaut Referer, du dernier appel (tronqué), s'il y en a un. */
    private String derniereOrigine;
}
