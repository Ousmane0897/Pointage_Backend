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
                .cors(cors -> cors.configurationSource(request -> {

                    CorsConfiguration config = new CorsConfiguration();

                    config.setAllowedOrigins(List.of(
                            "https://app.pointic-cleanic.com",
                            "https://pointic-cleanic.com",
                            "http://localhost:4200"
                    ));

                    config.setAllowedMethods(List.of(
                            "GET",
                            "POST",
                            "PUT",
                            "DELETE",
                            "OPTIONS"
                    ));

                    config.setAllowedHeaders(List.of(
                            "Authorization",
                            "Content-Type",
                            "Accept",
                            "Origin"
                    ));

                    config.setExposedHeaders(List.of("Authorization"));

                    config.setAllowCredentials(true);

                    config.setMaxAge(86400L);

                    return config;
                }))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth

                        // OPTIONS préflight autorisé
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 🔹 Routes publiques (pointage)
                        .requestMatchers("/pointages/**", "/api/pointages/**").permitAll()

                        // 🔹 Routes publiques docs / login
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

                        // 🔹 Routes publiques images
                        .requestMatchers(
                                "/api/produits/image/**",
                                "/api/employe-complet/image/**"
                        ).permitAll()

                        // 🔹 Routes sécurisées (JWT obligatoire)
                        .requestMatchers("/api/dashboard_par_agence").authenticated()
                        .requestMatchers("/api/planification/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/ferie/**").authenticated()
                        .requestMatchers("/api/employes/**").authenticated()
                        .requestMatchers("/api/super-admin/**").authenticated()

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