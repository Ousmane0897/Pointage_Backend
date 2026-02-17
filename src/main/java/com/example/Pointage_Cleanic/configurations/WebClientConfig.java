package com.example.Pointage_Cleanic.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient googleMapsWebClient() {
        return WebClient.builder()
                .baseUrl("https://maps.googleapis.com/maps/api")
                .build();
    }
}

