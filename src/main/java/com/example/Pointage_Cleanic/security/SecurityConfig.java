package com.example.Pointage_Cleanic.security;


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
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();

                    // ✅ Autoriser ton frontend réel
                    config.setAllowedOriginPatterns(List.of(
                            "https://app.pointic-cleanic.com",
                            "https://pointic-cleanic.com",
                            "https://*.ngrok-free.dev",
                            "http://localhost",
                            "http://127.0.0.1:*"
                    ));

                    // ✅ Autoriser tous les headers utiles
                    config.setAllowedHeaders(List.of(
                            "*"
                    ));

                    // ✅ Autoriser tous les verbes HTTP
                    config.setAllowedMethods(List.of(
                            "*"
                    ));

                    // ✅ Exposer les headers nécessaires (ex: Authorization)
                    config.setExposedHeaders(List.of(
                            "Authorization"
                    ));

                    // ✅ Indispensable si tu envoies JWT
                    config.setAllowCredentials(true);

                    return config;
                }))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth

                        // 🔹 OPTIONS préflight autorisé
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 🔹 Routes publiques
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

                        // 🔹 Routes sécurisées
                        .requestMatchers(HttpMethod.DELETE, "/api/ferie/**").authenticated()
                        .requestMatchers("/api/dashboard_par_agence").authenticated()
                        .requestMatchers("/api/planification/**").authenticated()

                        // 🔹 Routes pointage accessibles depuis le front
                        .requestMatchers("/pointages/**", "/api/pointages/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/api/produits/image/**").permitAll()
                        .requestMatchers("/api/employe-complet/image/**").permitAll()

                        // 🔹 Toutes les autres requêtes nécessitent authentification
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