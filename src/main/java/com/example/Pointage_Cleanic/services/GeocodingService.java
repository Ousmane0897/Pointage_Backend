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

        String neighborhood = null;
        String locality = null;
        String country = null;

        for (Map<String, Object> result : results) {

            List<Map<String, Object>> components =
                    (List<Map<String, Object>>) result.get("address_components");

            for (Map<String, Object> component : components) {

                List<String> types = (List<String>) component.get("types");
                String longName = (String) component.get("long_name");

                if (types.contains("neighborhood") || types.contains("sublocality")) {
                    neighborhood = longName;
                }

                if (types.contains("locality")) {
                    locality = longName;
                }

                if (types.contains("country")) {
                    country = longName;
                }
            }
        }

        // Construction propre
        if (neighborhood != null && locality != null) {
            return neighborhood + ", " + locality;
        }

        if (locality != null) {
            return locality;
        }

        return country != null ? country : "Adresse non disponible";
    }
}

