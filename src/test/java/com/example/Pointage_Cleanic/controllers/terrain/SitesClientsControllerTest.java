package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.SiteClientDto;
import com.example.Pointage_Cleanic.security.JwtRequestFilter;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import com.example.Pointage_Cleanic.services.terrain.SiteClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie qu'un site peut être créé / modifié SANS bloc GPS
 * (le frontend ne saisit plus latitude/longitude/rayon).
 */
@WebMvcTest(SitesClientsController.class)
@AutoConfigureMockMvc(addFilters = false)
class SitesClientsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SiteClientService service;

    // === Sécurité (OBLIGATOIRE en WebMvcTest même avec addFilters=false)
    @MockBean private MyUserDetailsService myUserDetailsService;
    @MockBean private JwtUtil jwtUtil;
    @MockBean private JwtRequestFilter jwtRequestFilter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Payload réaliste tel qu'envoyé par le front : aucun champ coordonnees / rayonToleranceM. */
    private static final String SITE_SANS_GPS_JSON = """
            {
              "code": "SITE-001",
              "nom": "Site Sans GPS",
              "adresse": "Rue du Test",
              "ville": "Dakar",
              "frequencePassage": "QUOTIDIEN",
              "actif": true
            }
            """;

    @Test
    void create_site_sans_gps_ok() throws Exception {
        when(service.create(any(SiteClientDto.class), any()))
                .thenReturn(SiteClientDto.builder().code("SITE-001").nom("Site Sans GPS").build());

        MockMultipartFile sitePart = new MockMultipartFile(
                "site", "site.json", MediaType.APPLICATION_JSON_VALUE, SITE_SANS_GPS_JSON.getBytes());

        mockMvc.perform(multipart("/api/terrain/sites-clients").file(sitePart))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SITE-001"));

        ArgumentCaptor<SiteClientDto> captor = ArgumentCaptor.forClass(SiteClientDto.class);
        verify(service).create(captor.capture(), any());
        SiteClientDto sent = captor.getValue();
        assertThat(sent.getCoordonnees()).isNull();
        assertThat(sent.getRayonToleranceM()).isNull();
    }

    @Test
    void update_site_sans_gps_ok() throws Exception {
        when(service.update(eq("1"), any(SiteClientDto.class), any()))
                .thenReturn(SiteClientDto.builder().code("SITE-001").nom("Site Sans GPS").build());

        MockMultipartFile sitePart = new MockMultipartFile(
                "site", "site.json", MediaType.APPLICATION_JSON_VALUE, SITE_SANS_GPS_JSON.getBytes());

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/terrain/sites-clients/{id}", "1").file(sitePart))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SITE-001"));

        ArgumentCaptor<SiteClientDto> captor = ArgumentCaptor.forClass(SiteClientDto.class);
        verify(service).update(eq("1"), captor.capture(), any());
        SiteClientDto sent = captor.getValue();
        assertThat(sent.getCoordonnees()).isNull();
        assertThat(sent.getRayonToleranceM()).isNull();
    }
}
