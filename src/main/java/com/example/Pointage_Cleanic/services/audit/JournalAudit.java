package com.example.Pointage_Cleanic.services.audit;

import com.example.Pointage_Cleanic.Enum.audit.ActionAudit;
import com.example.Pointage_Cleanic.configurations.CorrelationId;
import com.example.Pointage_Cleanic.entities.audit.EntreeAudit;
import com.example.Pointage_Cleanic.entities.audit.EntreeAudit.Changement;
import com.example.Pointage_Cleanic.security.Sensible;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Journal d'audit (lot 1 / 1b, décision D6 ; CDC règle 5).
 *
 * <ul>
 *   <li>Écrit dans la <b>transaction métier</b> : la ligne n'existe que si l'action a été
 *       enregistrée. Un appel hors transaction est refusé.</li>
 *   <li>Différentiel champ par champ entre l'état relu avant l'action et l'état après (rendus en
 *       JSON par Jackson, donc sans les champs {@code @JsonIgnore} ni {@code WRITE_ONLY}) ; les
 *       champs imbriqués ont un chemin pointé ({@code piece.numero}), les listes sont comparées
 *       en bloc. Hors différentiel : {@code version}, {@code updatedAt}, {@code historique}.</li>
 *   <li>Champs {@link Sensible} : écrits {@value #MASQUE}, avant comme après ; le changement
 *       reste visible (on sait que le champ a changé, pas sa valeur).</li>
 *   <li>Auteur ({@link CurrentUserProvider}), IP de la requête, identifiant de corrélation.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class JournalAudit {

    public static final String COLLECTION = "journal_audit";
    public static final String MASQUE = "***";
    static final Set<String> IGNORES = Set.of("version", "updatedAt", "historique");

    private static final Map<Class<?>, Set<String>> SENSIBLES = new ConcurrentHashMap<>();

    private final MongoTemplate mongoTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Enregistre une action.
     *
     * @param avant état relu au début de la transaction ({@code null} pour une création)
     * @param apres état après l'action ({@code null} si l'objet n'est pas modifié)
     */
    public EntreeAudit enregistrer(ActionAudit action, String objetType, String objetId, String numero,
                                   Object avant, Object apres, String motif) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Audit hors transaction : la ligne doit être écrite avec l'action qu'elle trace");
        }
        EntreeAudit entree = EntreeAudit.builder()
                .le(clock.instant())
                .compteId(currentUserProvider.currentUserId())
                .nomInstantane(currentUserProvider.currentUserNom())
                .action(action)
                .objetType(objetType)
                .objetId(objetId)
                .numero(numero)
                .changements(differentiel(avant, apres))
                .motif(motif)
                .ip(ipCourante())
                .correlationId(CorrelationId.courant())
                .build();
        return mongoTemplate.insert(entree, COLLECTION);
    }

    /** Différences entre deux états (l'un ou l'autre peut être nul). */
    List<Changement> differentiel(Object avant, Object apres) {
        Set<String> sensibles = new HashSet<>();
        if (avant != null) {
            sensibles.addAll(cheminsSensibles(avant.getClass()));
        }
        if (apres != null) {
            sensibles.addAll(cheminsSensibles(apres.getClass()));
        }
        Map<String, Object> av = aplatir(avant);
        Map<String, Object> ap = aplatir(apres);
        Set<String> champs = new TreeSet<>(av.keySet());
        champs.addAll(ap.keySet());

        List<Changement> changements = new ArrayList<>();
        for (String champ : champs) {
            if (IGNORES.contains(racine(champ))) {
                continue;
            }
            Object a = av.get(champ);
            Object b = ap.get(champ);
            if (Objects.equals(a, b)) {
                continue;
            }
            boolean masque = estSensible(champ, sensibles);
            changements.add(Changement.builder()
                    .champ(champ)
                    .avant(masque && a != null ? MASQUE : a)
                    .apres(masque && b != null ? MASQUE : b)
                    .build());
        }
        return changements;
    }

    private Map<String, Object> aplatir(Object objet) {
        Map<String, Object> plat = new LinkedHashMap<>();
        if (objet == null) {
            return plat;
        }
        Map<String, Object> arbre = objectMapper.convertValue(objet, new TypeReference<Map<String, Object>>() {
        });
        aplatir("", arbre, plat);
        return plat;
    }

    @SuppressWarnings("unchecked")
    private static void aplatir(String prefixe, Map<String, Object> arbre, Map<String, Object> plat) {
        for (Map.Entry<String, Object> e : arbre.entrySet()) {
            String chemin = prefixe.isEmpty() ? e.getKey() : prefixe + "." + e.getKey();
            if (e.getValue() instanceof Map<?, ?> m && !m.isEmpty()) {
                aplatir(chemin, (Map<String, Object>) m, plat);
            } else if (e.getValue() != null) {
                plat.put(chemin, e.getValue());
            }
        }
    }

    private static String racine(String champ) {
        int i = champ.indexOf('.');
        return i < 0 ? champ : champ.substring(0, i);
    }

    private static boolean estSensible(String champ, Set<String> sensibles) {
        for (String s : sensibles) {
            if (champ.equals(s) || champ.startsWith(s + ".")) {
                return true;
            }
        }
        return false;
    }

    /** Chemins des champs {@link Sensible} d'une classe, objets imbriqués compris. */
    static Set<String> cheminsSensibles(Class<?> classe) {
        return SENSIBLES.computeIfAbsent(classe, c -> {
            Set<String> chemins = new HashSet<>();
            collecter(c, "", chemins, new HashSet<>());
            return Set.copyOf(chemins);
        });
    }

    /** {@code chemin} : classes en cours de visite (protège des cycles sans ignorer un type répété). */
    private static void collecter(Class<?> classe, String prefixe, Set<String> chemins, Set<Class<?>> chemin) {
        if (classe == null || !estClasseApplicative(classe) || !chemin.add(classe)) {
            return;
        }
        try {
            collecterChamps(classe, prefixe, chemins, chemin);
        } finally {
            chemin.remove(classe);
        }
    }

    private static void collecterChamps(Class<?> classe, String prefixe, Set<String> chemins, Set<Class<?>> vus) {
        for (Class<?> c = classe; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                String chemin = prefixe + f.getName();
                if (f.isAnnotationPresent(Sensible.class)) {
                    chemins.add(chemin);
                } else if (!Collection.class.isAssignableFrom(f.getType()) && !f.getType().isEnum()) {
                    collecter(f.getType(), chemin + ".", chemins, vus);
                }
            }
        }
    }

    private static boolean estClasseApplicative(Class<?> c) {
        return !c.isPrimitive() && !c.isArray() && c.getName().startsWith("com.example.");
    }

    private static String ipCourante() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
