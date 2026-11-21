package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize("@droits.peut(...)") — voir security/Droits
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtRequestFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // ✅ CORS configuration
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();

                    // Origines du front : liste unique partagée avec le WebSocket (OriginesAutorisees).
                    config.setAllowedOriginPatterns(OriginesAutorisees.MOTIFS);

                    // Méthodes autorisées
                    config.setAllowedMethods(List.of(
                            "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
                    ));

                    // Headers autorisés
                    config.setAllowedHeaders(List.of("*"));

                    // Exposer l'Authorization pour le JWT
                    config.setExposedHeaders(List.of("Authorization"));

                    // Indispensable pour JWT dans header
                    config.setAllowCredentials(true);

                    config.setMaxAge(3600L); // Cache preflight 1h
                    return config;
                }))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth

                        // OPTIONS préflight autorisé pour toutes les routes
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 🔹 Pointages — vues superviseur (JWT obligatoire).
                        // Placées AVANT les règles publiques (first-match-wins). Le GET public
                        // /api/pointages/{codeSecret} a été supprimé (lot 0 sécurité, C5).
                        .requestMatchers(HttpMethod.GET, "/api/pointages/today").authenticated()
                        .requestMatchers("/api/pointages/historique/**").authenticated() // search + exports excel/pdf
                        .requestMatchers(HttpMethod.GET, "/api/pointages").authenticated() // getAll

                        // 🔹 Pointages — mobile public (pointage sans token)
                        .requestMatchers(HttpMethod.POST, "/api/pointages").permitAll()
                        .requestMatchers("/pointages/**").permitAll() // chemin legacy mobile inchangé

                        // 🔹 Connexion : seul le POST est public. change-password exige le JWT
                        // (fallback anyRequest), GET/DELETE /api/login ont été supprimés.
                        .requestMatchers(HttpMethod.POST, "/api/login").permitAll()

                        // 🔹 Autres routes publiques
                        .requestMatchers(
                                "/auth/forgot-password",
                                "/auth/reset-password/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/error",
                                "/error/**"
                        ).permitAll()
                        .requestMatchers(
                                "/api/produits/image/**",
                                "/api/employe-complet/image/**",
                                "/api/dossier-employe/*/photo"
                        ).permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // 🔹 Routes sécurisées (JWT obligatoire)
                        .requestMatchers("/api/dashboard_par_agence").authenticated()
                        .requestMatchers("/api/planification/**").authenticated()
                        .requestMatchers("/ws/info").authenticated()
                        .requestMatchers("/api/terrain/**").authenticated()
                        .requestMatchers("/api/stock/**").authenticated()

                        // Toutes les autres requêtes nécessitent authentification
                        .anyRequest().authenticated()
                )
                // Requête non authentifiée (aucun header Authorization) → 401, aligné sur
                // JwtRequestFilter (token invalide/expiré → 401). Depuis le lot 0 sécurité, un 403
                // signifie « authentifié mais sans le droit » (@PreAuthorize + Droits, compte
                // désactivé) — renvoyé en JSON par GlobalExceptionHandler.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentification requise")))
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Salted hash
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}