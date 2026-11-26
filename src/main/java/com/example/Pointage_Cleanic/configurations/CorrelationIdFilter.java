package com.example.Pointage_Cleanic.configurations;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Pose l'identifiant de corrélation de chaque requête (lot 1 / 1b) : l'en-tête
 * {@value CorrelationId#EN_TETE} s'il contient un UUID, sinon un nouvel UUID. Il est renvoyé dans
 * la réponse et repris par le journal d'audit et les événements de domaine.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = CorrelationId.depuisEnTete(request.getHeader(CorrelationId.EN_TETE));
        response.setHeader(CorrelationId.EN_TETE, id);
        org.slf4j.MDC.put(CorrelationId.CLE, id);
        try {
            chain.doFilter(request, response);
        } finally {
            org.slf4j.MDC.remove(CorrelationId.CLE);
        }
    }
}
