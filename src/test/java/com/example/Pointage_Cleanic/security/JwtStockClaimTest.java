package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Stock;
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
 * Non-régression : le vrai {@link JwtUtil} ré-émet le bloc {@code stock} dans le claim
 * JWT {@code modules}, exactement comme {@code terrain}/{@code productionChimie}.
 *
 * <p>Dans {@code LoginControllerTest}, {@code JwtUtil} est mocké : la sérialisation réelle
 * de l'objet {@code ModulesAutorises} dans le token n'y est jamais exercée. Ce test la couvre.
 */
class JwtStockClaimTest {

    private static final String SECRET = "test-secret-tres-long-de-plus-de-32-caracteres";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        jwtUtil.init();
    }

    @Test
    void le_bloc_stock_partiel_est_present_dans_le_claim_modules() {
        // --- stock PARTIEL : quelques flags à true, le reste à false ---
        Stock stock = new Stock();
        stock.setCatalogue(true);
        stock.setBonsSortie(true);
        stock.setMarges(true);

        ModulesAutorises modules = new ModulesAutorises();
        modules.setStock(stock);
        // terrain / productionChimie laissés null => doivent être omis (@JsonInclude(NON_NULL))

        UserDetails userDetails = new User(
                "admin@test.com", "x",
                List.of(new SimpleGrantedAuthority("ROLE_SUPERVISEUR")));

        String token = jwtUtil.generateToken(
                userDetails, "Ali", "Diallo", RoleAdmin.SUPERVISEUR,
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

        Object stockClaim = modulesClaim.get("stock");
        assertInstanceOf(Map.class, stockClaim, "le bloc stock doit être sérialisé dans le claim");

        @SuppressWarnings("unchecked")
        Map<String, Object> stockMap = (Map<String, Object>) stockClaim;
        assertEquals(Boolean.TRUE, stockMap.get("catalogue"));
        assertEquals(Boolean.TRUE, stockMap.get("bonsSortie"));
        assertEquals(Boolean.TRUE, stockMap.get("marges"));
        // flag non positionné => false (rétro-compatibilité)
        assertEquals(Boolean.FALSE, stockMap.get("inventaires"));

        // même traitement que les autres sous-modules : null => omis du claim
        assertFalse(modulesClaim.containsKey("terrain"));
        assertFalse(modulesClaim.containsKey("productionChimie"));
    }
}
