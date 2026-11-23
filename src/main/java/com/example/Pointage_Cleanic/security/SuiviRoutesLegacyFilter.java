package com.example.Pointage_Cleanic.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lot 0 sécurité (C7) — <b>compte les appels aux routes en sursis</b>, faute d'accès aux journaux
 * nginx pour prouver qu'aucun client ne les utilise encore.
 *
 * <p>Placé avant la chaîne Spring Security : les appels refusés (401/403) sont comptés aussi, avec
 * leur code. Les compteurs se lisent sur {@code GET /api/securite/routes-legacy} (SUPERADMIN).
 * Si une route reste à zéro sur plusieurs semaines d'usage normal, la supprimer et la retirer
 * de {@link #ROUTES} (et, pour les images, du {@code permitAll} et de {@code RoutesPubliquesIT}).
 *
 * <p>Un échec d'écriture du compteur ne fait jamais échouer la requête : il est loggé en WARN.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SuiviRoutesLegacyFilter extends OncePerRequestFilter {

    record RouteSurveillee(String methode, String motif) {
        String libelle() {
            return methode + " " + motif;
        }
    }

    static final List<RouteSurveillee> ROUTES = List.of(
            new RouteSurveillee("GET", "/api/produits/image/**"),
            new RouteSurveillee("GET", "/api/employe-complet/image/**"),
            new RouteSurveillee("POST", "/api/terrain/pointages")
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    /** Absent dans les tests en tranche ({@code @WebMvcTest}) : le filtre ne fait alors rien. */
    private final ObjectProvider<SuiviRoutesLegacyService> service;

    public SuiviRoutesLegacyFilter(ObjectProvider<SuiviRoutesLegacyService> service) {
        this.service = service;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RouteSurveillee route = trouver(request);
        if (route == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            enregistrer(route, request, response.getStatus());
        }
    }

    private static RouteSurveillee trouver(HttpServletRequest request) {
        String chemin = request.getRequestURI().substring(request.getContextPath().length());
        for (RouteSurveillee r : ROUTES) {
            if (r.methode().equalsIgnoreCase(request.getMethod()) && MATCHER.match(r.motif(), chemin)) {
                return r;
            }
        }
        return null;
    }

    private void enregistrer(RouteSurveillee route, HttpServletRequest request, int statut) {
        SuiviRoutesLegacyService s = service.getIfAvailable();
        if (s == null) {
            return;
        }
        String origine = request.getHeader(HttpHeaders.ORIGIN);
        if (origine == null) {
            origine = request.getHeader(HttpHeaders.REFERER);
        }
        log.warn("Appel d'une route en sursis (C7) : {} -> {}", route.libelle(), statut);
        try {
            s.enregistrer(route.libelle(), statut, request.getHeader(HttpHeaders.USER_AGENT), origine);
        } catch (RuntimeException e) {
            log.warn("Compteur des routes en sursis non mis à jour ({}) : {}", route.libelle(), e.toString());
        }
    }
}
