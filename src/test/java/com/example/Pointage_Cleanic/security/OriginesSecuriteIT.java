package com.example.Pointage_Cleanic.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C6 — origines : même liste pour le CORS HTTP et le point d'entrée WebSocket. */
class OriginesSecuriteIT extends SecuriteITBase {

    private static final String FRONT = "https://pointic-cleanic.com";
    private static final String PIRATE = "https://site-pirate.example";

    @Test
    void preflight_http_origine_autorisee() throws Exception {
        mockMvc.perform(options("/api/login").header(HttpHeaders.ORIGIN, FRONT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONT));
    }

    @Test
    void preflight_http_origine_inconnue_refusee() throws Exception {
        mockMvc.perform(options("/api/login").header(HttpHeaders.ORIGIN, PIRATE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void websocket_origine_inconnue_refusee() throws Exception {
        mockMvc.perform(get("/ws/info").header(HttpHeaders.ORIGIN, PIRATE))
                .andExpect(status().isForbidden());
    }

    @Test
    void websocket_origine_autorisee() throws Exception {
        mockMvc.perform(get("/ws/info").header(HttpHeaders.ORIGIN, FRONT))
                .andExpect(status().isOk());
    }
}
