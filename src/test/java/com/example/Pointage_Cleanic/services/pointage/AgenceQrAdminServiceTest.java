package com.example.Pointage_Cleanic.services.pointage;

import com.example.Pointage_Cleanic.Dto.pointage.AgenceQrDto;
import com.example.Pointage_Cleanic.entities.terrain.CoordonneesGps;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.PointageAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgenceQrAdminServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Africa/Dakar"));

    private final SiteClientRepository repository = mock(SiteClientRepository.class);
    private final ParametresPointageService parametres = mock(ParametresPointageService.class);
    private final AgenceQrService agenceQrService = new AgenceQrService(repository, CLOCK);
    private final AgenceQrAdminService service = new AgenceQrAdminService(repository, agenceQrService, parametres);

    private SiteClient geolocalisee;
    private SiteClient sansGps;

    @BeforeEach
    void setUp() {
        geolocalisee = SiteClient.builder().id("A").code("A").nom("SGS Mermoz").actif(true)
                .coordonnees(new CoordonneesGps(14.7, -17.45)).rayonToleranceM(80).build();
        agenceQrService.assurerToken(geolocalisee);
        sansGps = SiteClient.builder().id("B").code("B").nom("BGFI Tann").actif(true).build();
        agenceQrService.assurerToken(sansGps);

        when(repository.findAll(any(Sort.class))).thenReturn(List.of(geolocalisee, sansGps));
        when(repository.findById("A")).thenReturn(Optional.of(geolocalisee));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void liste_les_agences_avec_payload_version_et_etat_gps() {
        when(parametres.estAdministrateur()).thenReturn(true);

        List<AgenceQrDto> liste = service.lister();

        AgenceQrDto a = liste.get(0);
        assertEquals("POINTIC:AG:" + geolocalisee.getQrToken(), a.getPayload());
        assertEquals(1, a.getQrVersion());
        assertTrue(a.isGeolocalise());
        assertEquals(80, a.getRayonToleranceM());

        AgenceQrDto b = liste.get(1);
        assertFalse(b.isGeolocalise());
        assertNull(b.getLatitude());
        assertEquals(100, b.getRayonToleranceM(), "rayon par défaut affiché");
    }

    @Test
    void regenerer_renvoie_la_nouvelle_version() {
        when(parametres.estAdministrateur()).thenReturn(true);
        String ancien = geolocalisee.getQrToken();

        AgenceQrDto dto = service.regenerer("A");

        assertEquals(2, dto.getQrVersion());
        assertFalse(dto.getPayload().endsWith(ancien));
    }

    @Test
    void hors_rh_et_superadmin_tout_est_refuse() {
        when(parametres.estAdministrateur()).thenReturn(false);

        assertThrows(PointageAccesRefuseException.class, service::lister);
        assertThrows(PointageAccesRefuseException.class, () -> service.regenerer("A"));
        verify(repository, never()).save(any());
    }
}
