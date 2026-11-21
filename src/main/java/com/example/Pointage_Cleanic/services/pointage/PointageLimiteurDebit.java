package com.example.Pointage_Cleanic.services.pointage;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite d'appels sur la route publique {@code POST /api/pointages} (code à 4 chiffres, donc
 * 10 000 combinaisons : sans limite, il suffit de les essayer toutes).
 *
 * <p>Deux règles, appliquées séparément <b>par adresse IP</b> et <b>par appareil</b>
 * ({@code deviceId}) :
 * <ol>
 *   <li>au plus {@value #MAX_APPELS} appels par fenêtre glissante d'une minute ;</li>
 *   <li>après {@value #MAX_CODES_INCONNUS} codes inconnus d'affilée, blocage de 15 minutes. Un
 *       pointage réussi remet le compteur à zéro.</li>
 * </ol>
 *
 * <p>En mémoire, par instance : suffisant pour le déploiement actuel (une instance). L'IP est
 * celle de la connexion ; derrière le proxy, {@code server.forward-headers-strategy: native}
 * (profil prod) fait que seul un {@code X-Forwarded-For} posé par un mandataire interne est cru,
 * un client ne peut pas choisir son IP. Le {@code deviceId} est fourni par le client : la règle
 * par IP est donc la protection principale, celle par appareil un complément.
 */
@Component
public class PointageLimiteurDebit {

    static final int MAX_APPELS = 10;
    static final Duration FENETRE = Duration.ofMinutes(1);
    static final int MAX_CODES_INCONNUS = 5;
    static final Duration BLOCAGE = Duration.ofMinutes(15);
    private static final int PURGE_AU_DELA_DE = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Long>> appels = new ConcurrentHashMap<>();
    private final Map<String, Integer> echecs = new ConcurrentHashMap<>();
    private final Map<String, Long> bloqueJusqua = new ConcurrentHashMap<>();

    public PointageLimiteurDebit(Clock clock) {
        this.clock = clock;
    }

    /**
     * Enregistre l'appel et dit s'il doit être refusé.
     *
     * @return le message de refus (429), ou vide si l'appel est accepté
     */
    public Optional<String> verifier(String ip, String deviceId) {
        long maintenant = clock.millis();
        purgerSiNecessaire(maintenant);
        for (String cle : cles(ip, deviceId)) {
            Long fin = bloqueJusqua.get(cle);
            if (fin != null && fin > maintenant) {
                return Optional.of("Trop de codes invalides. Réessayez dans quelques minutes.");
            }
        }
        boolean depasse = false;
        for (String cle : cles(ip, deviceId)) {
            Deque<Long> fenetre = appels.computeIfAbsent(cle, k -> new ArrayDeque<>());
            synchronized (fenetre) {
                while (!fenetre.isEmpty() && fenetre.peekFirst() <= maintenant - FENETRE.toMillis()) {
                    fenetre.pollFirst();
                }
                fenetre.addLast(maintenant);
                depasse |= fenetre.size() > MAX_APPELS;
            }
        }
        return depasse
                ? Optional.of("Trop de tentatives de pointage. Réessayez dans une minute.")
                : Optional.empty();
    }

    /** Code inconnu : au {@value #MAX_CODES_INCONNUS}ᵉ d'affilée, blocage de 15 minutes. */
    public void signalerCodeInconnu(String ip, String deviceId) {
        long maintenant = clock.millis();
        for (String cle : cles(ip, deviceId)) {
            int n = echecs.merge(cle, 1, Integer::sum);
            if (n >= MAX_CODES_INCONNUS) {
                bloqueJusqua.put(cle, maintenant + BLOCAGE.toMillis());
                echecs.remove(cle);
            }
        }
    }

    /** Pointage réussi : remet à zéro le compteur de codes inconnus. */
    public void signalerSucces(String ip, String deviceId) {
        for (String cle : cles(ip, deviceId)) {
            echecs.remove(cle);
        }
    }

    private static String[] cles(String ip, String deviceId) {
        String a = "ip:" + (ip == null ? "?" : ip);
        return (deviceId == null || deviceId.isBlank()) ? new String[]{a} : new String[]{a, "app:" + deviceId};
    }

    private void purgerSiNecessaire(long maintenant) {
        if (appels.size() + bloqueJusqua.size() < PURGE_AU_DELA_DE) {
            return;
        }
        bloqueJusqua.values().removeIf(fin -> fin <= maintenant);
        appels.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long dernier = e.getValue().peekLast();
                return dernier == null || dernier <= maintenant - FENETRE.toMillis();
            }
        });
    }
}
