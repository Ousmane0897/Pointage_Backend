package com.example.Pointage_Cleanic.security;

import com.example.Pointage_Cleanic.security.JwtRequestFilter;
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

                    // Mettre explicitement ton front ici
                    config.setAllowedOrigins(List.of(
                            "https://pointic-cleanic.com",
                            "https://www.pointic-cleanic.com",
                            "https://app.pointic-cleanic.com",
                            "http://localhost",
                            "http://127.0.0.1:*",
                            "https://*.ngrok-free.dev",
                            "https://sizably-unhonored-bryce.ngrok-free.dev"
                    ));

                    // Méthodes autorisées
                    config.setAllowedMethods(List.of(
                            "GET", "POST", "PUT", "DELETE", "OPTIONS"
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
                        // Placées AVANT les règles publiques : first-match-wins, et
                        // /api/pointages/{codeSecret} matcherait aussi /today.
                        .requestMatchers(HttpMethod.GET, "/api/pointages/today").authenticated()
                        .requestMatchers("/api/pointages/historique/**").authenticated() // search + exports excel/pdf
                        .requestMatchers(HttpMethod.GET, "/api/pointages").authenticated() // getAll

                        // 🔹 Pointages — mobile public (pointage sans token)
                        .requestMatchers(HttpMethod.POST, "/api/pointages").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/pointages/{codeSecret}").permitAll() // statut consulté par le mobile
                        .requestMatchers("/pointages/**").permitAll() // chemin legacy mobile inchangé

                        // 🔹 Autres routes publiques
                        .requestMatchers(
                                "/api/login/**",
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
                        .requestMatchers(HttpMethod.DELETE, "/api/ferie/**").authenticated()
                        .requestMatchers("/ws/info").authenticated()
                        .requestMatchers("/api/super-admin/**").authenticated()
                        .requestMatchers("/api/terrain/**").authenticated()

                        // Toutes les autres requêtes nécessitent authentification
                        .anyRequest().authenticated()
                )
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