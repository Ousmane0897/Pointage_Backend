package com.example.Pointage_Cleanic.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeocodingService {

    @Value("${google.maps.api.key}")
    private String googleApiKey;

    private final WebClient webClient =
            WebClient.create("https://maps.googleapis.com/maps/api/geocode/json");

    public Mono<String> getReadableAddress(double lat, double lng) {

        String latLng = lat + "," + lng;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .queryParam("latlng", latLng)
                        .queryParam("key", googleApiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::extractHumanReadableAddress)
                .onErrorReturn("Adresse non disponible");
    }

    @SuppressWarnings("unchecked")
    private String extractHumanReadableAddress(Map<String, Object> response) {

        List<Map<String, Object>> results =
                (List<Map<String, Object>>) response.get("results");

        if (results == null || results.isEmpty()) {
            return "Adresse non disponible";
        }

        // 1️⃣ Priorité : formatted_address SANS Plus Code
        for (Map<String, Object> result : results) {
            String formatted = (String) result.get("formatted_address");
            if (formatted != null && !formatted.contains("+")) {
                return formatted;
            }
        }

        // 2️⃣ Fallback intelligent via address_components
        String quartier = null;
        String ville = null;
        String pays = null;

        for (Map<String, Object> result : results) {

            List<Map<String, Object>> components =
                    (List<Map<String, Object>>) result.get("address_components");

            if (components == null) continue;

            for (Map<String, Object> component : components) {

                List<String> types = (List<String>) component.get("types");
                String longName = (String) component.get("long_name");

                if (types == null || longName == null) continue;

                if (quartier == null &&
                        (types.contains("neighborhood")
                                || types.contains("sublocality")
                                || types.contains("sublocality_level_1"))) {
                    quartier = longName;
                }

                if (ville == null && types.contains("locality")) {
                    ville = longName;
                }

                if (pays == null && types.contains("country")) {
                    pays = longName;
                }
            }
        }

        // 3️⃣ Construction finale (UX métier)
        if (quartier != null && ville != null) {
            return quartier + ", " + ville;
        }

        if (ville != null) {
            return ville;
        }

        if (pays != null) {
            return pays;
        }

        // 4️⃣ Ultime fallback
        return "Localisation approximative";
    }


}

