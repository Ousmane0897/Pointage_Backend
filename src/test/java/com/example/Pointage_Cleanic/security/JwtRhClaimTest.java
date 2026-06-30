package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Rh;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Non-régression : le vrai {@link JwtUtil} ré-émet le bloc {@code rh} (objet de 19 sous-flags)
 * dans le claim JWT {@code modules}, exactement comme {@code stock}/{@code terrain}/
 * {@code productionChimie}, depuis le passage de {@code rh} booléen → objet.
 */
class JwtRhClaimTest {

    private static final String SECRET = "test-secret-tres-long-de-plus-de-32-caracteres";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        jwtUtil.init();
    }

    @Test
    void le_bloc_rh_partiel_est_present_dans_le_claim_modules() {
        // --- rh PARTIEL : quelques flags à true, le reste à false ---
        Rh rh = new Rh();
        rh.setDossierEmploye(true);
        rh.setConges(true);
        rh.setTableauBordRh(true);

        ModulesAutorises modules = new ModulesAutorises();
        modules.setRh(rh);
        // terrain / stock / productionChimie laissés null => doivent être omis (@JsonInclude(NON_NULL))

        UserDetails userDetails = new User(
                "admin@test.com", "x",
                List.of(new SimpleGrantedAuthority("ROLE_RH")));

        String token = jwtUtil.generateToken(
                userDetails, "Ali", "Diallo", RoleAdmin.RH,
                "admin@test.com", "Poste", false, modules);

        // --- re-parse du token avec la même clé ---
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        @SuppressWarnings("unchecked")
        Map<String, Object> modulesClaim = claims.get("modules", Map.class);
        assertNotNull(modulesClaim, "le claim modules doit être présent");

        Object rhClaim = modulesClaim.get("rh");
        assertInstanceOf(Map.class, rhClaim, "le bloc rh doit être sérialisé comme objet dans le claim");

        @SuppressWarnings("unchecked")
        Map<String, Object> rhMap = (Map<String, Object>) rhClaim;
        assertEquals(Boolean.TRUE, rhMap.get("dossierEmploye"));
        assertEquals(Boolean.TRUE, rhMap.get("conges"));
        assertEquals(Boolean.TRUE, rhMap.get("tableauBordRh"));
        // flag non positionné => false (rétro-compatibilité)
        assertEquals(Boolean.FALSE, rhMap.get("grilleSalariale"));

        // même traitement que les autres sous-modules : null => omis du claim
        assertFalse(modulesClaim.containsKey("terrain"));
        assertFalse(modulesClaim.containsKey("stock"));
        assertFalse(modulesClaim.containsKey("productionChimie"));
    }
}