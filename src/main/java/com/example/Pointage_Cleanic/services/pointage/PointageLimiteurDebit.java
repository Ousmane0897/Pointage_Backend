package com.example.Pointage_Cleanic.services.pointage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
 * ({@code deviceId}), seuils réglables par propriétés {@code pointage.limite.*} :
 * <ol>
 *   <li>au plus {@code par-ip-par-minute} (60) appels par IP et {@code par-appareil-par-minute}
 *       (10) par appareil, par fenêtre glissante d'une minute. Le seuil par IP est large : un
 *       site peut faire pointer beaucoup d'agents derrière une seule IP (NAT, proxy) ;</li>
 *   <li>après {@code codes-inconnus-avant-blocage} (5) codes inconnus d'affilée, blocage de
 *       {@code duree-blocage-minutes} (15). Un pointage réussi remet le compteur à zéro.</li>
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

    static final Duration FENETRE = Duration.ofMinutes(1);
    private static final int PURGE_AU_DELA_DE = 10_000;
    private static final String PREFIXE_IP = "ip:";

    private final Clock clock;
    private final int maxAppelsParIp;
    private final int maxAppelsParAppareil;
    private final int maxCodesInconnus;
    private final Duration blocage;
    private final Map<String, Deque<Long>> appels = new ConcurrentHashMap<>();
    private final Map<String, Integer> echecs = new ConcurrentHashMap<>();
    private final Map<String, Long> bloqueJusqua = new ConcurrentHashMap<>();

    @Autowired
    public PointageLimiteurDebit(
            Clock clock,
            @Value("${pointage.limite.par-ip-par-minute:60}") int maxAppelsParIp,
            @Value("${pointage.limite.par-appareil-par-minute:10}") int maxAppelsParAppareil,
            @Value("${pointage.limite.codes-inconnus-avant-blocage:5}") int maxCodesInconnus,
            @Value("${pointage.limite.duree-blocage-minutes:15}") long dureeBlocageMinutes) {
        this.clock = clock;
        this.maxAppelsParIp = maxAppelsParIp;
        this.maxAppelsParAppareil = maxAppelsParAppareil;
        this.maxCodesInconnus = maxCodesInconnus;
        this.blocage = Duration.ofMinutes(dureeBlocageMinutes);
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
                int max = cle.startsWith(PREFIXE_IP) ? maxAppelsParIp : maxAppelsParAppareil;
                depasse |= fenetre.size() > max;
            }
        }
        return depasse
                ? Optional.of("Trop de tentatives de pointage. Réessayez dans une minute.")
                : Optional.empty();
    }

    /** Code inconnu : au n-ième d'affilée ({@code codes-inconnus-avant-blocage}), blocage. */
    public void signalerCodeInconnu(String ip, String deviceId) {
        long maintenant = clock.millis();
        for (String cle : cles(ip, deviceId)) {
            int n = echecs.merge(cle, 1, Integer::sum);
            if (n >= maxCodesInconnus) {
                bloqueJusqua.put(cle, maintenant + blocage.toMillis());
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
        String a = PREFIXE_IP + (ip == null ? "?" : ip);
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
