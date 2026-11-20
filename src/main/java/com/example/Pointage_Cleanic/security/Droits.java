package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Contrôle serveur des droits par module, utilisé dans les {@code @PreAuthorize} :
 * <pre>{@code @PreAuthorize("@droits.peut('rh.grilleSalariale')")}
 * @PreAuthorize("@droits.peutUnParmi('stock.catalogue', 'stock.etatStock')")
 * @PreAuthorize("@droits.estSuperAdmin()")}</pre>
 *
 * <p><b>Source</b> : les {@code modulesAutorises} de l'utilisateur <b>relus en base</b> à chaque
 * appel — jamais le claim {@code modules} du JWT, qui reste valable 8 h après un retrait de droit.
 *
 * <p><b>Règles</b> (alignées sur la sidebar du front, {@code hasAccess} / {@code accesRh}) :
 * <ul>
 *   <li>compte {@code Utilisateur} désactivé ({@code active=false}) → toujours refusé ;</li>
 *   <li>rôle {@code SUPERADMIN} (collection {@code login}) → tout est permis ;</li>
 *   <li>rôle {@code RH} → tout drapeau {@code rh.*} est permis ;</li>
 *   <li>{@code "module.drapeau"} → vrai si ce booléen vaut {@code true} ;</li>
 *   <li>{@code "module.*"} → vrai si au moins un drapeau du module vaut {@code true} ;</li>
 *   <li>{@code "dashboard"} / {@code "admin"} → booléens de premier niveau.</li>
 * </ul>
 * Un ancien {@code rh: true} est déjà converti en « tous les drapeaux RH » par {@code RhDeserializer}.
 *
 * <p>Ce composant ne remplace pas les contrôles de rôle déjà écrits dans les services
 * ({@code HabilitationStock}, {@code CongeIdentiteService}…) : il s'y ajoute.
 */
@Component("droits")
@RequiredArgsConstructor
public class Droits {

    static final String ROLE_SUPERADMIN = "SUPERADMIN";
    static final String ROLE_RH = "RH";

    private final CurrentUserProvider currentUserProvider;
    private final UtilisateurRepository utilisateurRepository;
    private final LoginRepository loginRepository;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Super-administrateur actif. */
    public boolean estSuperAdmin() {
        Contexte c = contexte();
        return c != null && c.actif() && ROLE_SUPERADMIN.equals(c.role());
    }

    /** Vrai si l'utilisateur courant dispose du drapeau (voir les règles de la classe). */
    public boolean peut(String drapeau) {
        return peutUnParmi(drapeau);
    }

    /** Vrai si l'utilisateur courant dispose d'au moins un des drapeaux. */
    public boolean peutUnParmi(String... drapeaux) {
        Contexte c = contexte();
        if (c == null || !c.actif()) {
            return false;
        }
        if (ROLE_SUPERADMIN.equals(c.role())) {
            return true;
        }
        for (String d : drapeaux) {
            if (evaluer(c, d)) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluer(Contexte c, String drapeau) {
        if (drapeau == null || drapeau.isBlank()) {
            return false;
        }
        String[] parties = drapeau.split("\\.", 2);
        String module = parties[0];
        if ("rh".equals(module) && ROLE_RH.equals(c.role())) {
            return true;
        }
        Object valeurModule = c.modules().get(module);
        if (parties.length == 1) {
            return Boolean.TRUE.equals(valeurModule);
        }
        if (!(valeurModule instanceof Map<?, ?> sousModule)) {
            return false;
        }
        String flag = parties[1];
        if ("*".equals(flag)) {
            return sousModule.values().stream().anyMatch(Boolean.TRUE::equals);
        }
        return Boolean.TRUE.equals(sousModule.get(flag));
    }

    private Contexte contexte() {
        String email = currentUserProvider.currentEmail();
        if (email == null || "anonymousUser".equals(email)) {
            return null;
        }
        Optional<Utilisateur> profil = utilisateurRepository.findByEmail(email);
        String role = profil.map(Utilisateur::getRole).map(Enum::name)
                .or(() -> loginRepository.findByEmail(email).map(User::getRole))
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .orElse(null);
        boolean actif = profil.map(Utilisateur::isActive).orElse(true);
        Map<String, Object> modules = profil.map(Utilisateur::getModulesAutorises)
                .map(m -> mapper.convertValue(m, MAP_TYPE))
                .orElse(Map.of());
        return new Contexte(role, actif, modules);
    }

    private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> MAP_TYPE =
            new com.fasterxml.jackson.core.type.TypeReference<>() {
            };

    private record Contexte(String role, boolean actif, Map<String, Object> modules) {
    }
}
