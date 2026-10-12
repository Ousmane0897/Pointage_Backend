package com.example.Pointage_Cleanic.services.pointage;

import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgenceQrServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Africa/Dakar"));

    private final SiteClientRepository repository = mock(SiteClientRepository.class);
    private final AgenceQrService service = new AgenceQrService(repository, CLOCK);

    @Test
    void jeton_de_24_octets_en_base64_url_sans_padding() {
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = AgenceQrService.genererToken();
            assertEquals(32, token.length());
            assertTrue(token.matches("[A-Za-z0-9_-]+"), token);
            assertTrue(vus.add(token), "jeton dupliqué");
        }
    }

    @Test
    void assurer_token_pose_la_version_1_sans_ecraser_un_jeton_existant() {
        SiteClient site = SiteClient.builder().id("A").build();

        assertTrue(service.assurerToken(site));
        assertEquals(1, site.getQrVersion());
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 0), site.getQrGenereLe());

        String token = site.getQrToken();
        assertFalse(service.assurerToken(site));
        assertEquals(token, site.getQrToken());
    }

    @Test
    void regenerer_change_le_jeton_et_incremente_la_version() {
        SiteClient site = SiteClient.builder().id("A").build();
        service.assurerToken(site);
        String ancien = site.getQrToken();
        when(repository.findById("A")).thenReturn(Optional.of(site));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.regenerer("A");
        service.regenerer("A");

        assertNotEquals(ancien, site.getQrToken());
        assertEquals(3, site.getQrVersion());
    }

    @Test
    void extraction_du_jeton() {
        assertEquals("abc", AgenceQrService.extraireToken("POINTIC:AG:abc"));
        assertEquals("abc", AgenceQrService.extraireToken("  POINTIC:AG:abc "));
        assertNull(AgenceQrService.extraireToken("POINTIC:AG:"));
        assertNull(AgenceQrService.extraireToken("abc"));
        assertNull(AgenceQrService.extraireToken(null));
    }

    @Test
    void url_imprimee_encode_le_payload_derriere_la_page_d_accueil() {
        SiteClient site = SiteClient.builder().id("A").qrToken("tok_-1").build();

        assertEquals("POINTIC:AG:tok_-1", service.url(site), "sans base-url : payload brut");

        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://pointic-cleanic.com/");
        assertEquals("https://pointic-cleanic.com/home?qr=POINTIC%3AAG%3Atok_-1", service.url(site));
    }
}
