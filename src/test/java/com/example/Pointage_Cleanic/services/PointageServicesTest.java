package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class PointageServicesTest {

    @InjectMocks
    private PointageServices service;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private PointageRepository pointageRepository;

    @Mock
    private DossierEmployeRepository dossierEmployeRepository;

    private DossierEmploye agent(String agentId, String siteAffecte) {
        DossierEmploye d = new DossierEmploye();
        d.setAgentId(agentId);
        d.setPrenom("Mamadou");
        d.setNom("Diop");
        d.setSiteAffecte(siteAffecte);
        return d;
    }

    // ======================================================
    // 🧪 canPoint()
    // ======================================================

    @Test
    void should_allow_point_when_no_recent_device_usage() {

        when(pointageRepository.existsByDeviceIdAndTimestampAfter(anyString(), any()))
                .thenReturn(false);

        boolean result = service.canPoint("DEV-1", 4);

        assertTrue(result);
    }

    // ======================================================
    // 🧪 decouperSites() — découpage multi-sites "/"
    // ======================================================

    @Test
    void should_split_multi_sites_on_slash() {
        assertArrayEquals(
                new String[]{"Keur gorgui", "yoff", "bgfi tann"},
                PointageServices.decouperSites("Keur gorgui / yoff / bgfi tann"));
        assertArrayEquals(
                new String[]{"Ouakam", "zone A"},
                PointageServices.decouperSites("Ouakam / zone A"));
        assertArrayEquals(new String[0], PointageServices.decouperSites(null));
        assertArrayEquals(new String[0], PointageServices.decouperSites("   "));
    }

    // ======================================================
    // 🧪 enregistrerPointage() — NO EXISTING POINTAGE (ARRIVÉE)
    // ======================================================

    @Test
    void should_create_new_pointage_when_none_exists() {

        when(dossierEmployeRepository.findByAgentId("1234"))
                .thenReturn(Optional.of(agent("1234", "Ouakam / zone A")));
        when(pointageRepository.findFirstByCodeSecretAndDateAndHeureDepartIsNullOrderByTimestampDesc(
                eq("1234"), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pointage result = service.enregistrerPointage("1234", "DEV-1", 14.717, -17.467);

        assertNotNull(result);
        assertEquals("1234", result.getCodeSecret());
        assertEquals("EN COURS...", result.getStatus());
        assertArrayEquals(new String[]{"Ouakam", "zone A"}, result.getSite());
        assertNotNull(result.getHeureArrive());
        verify(pointageRepository).save(any());
    }

    // ======================================================
    // 🧪 enregistrerPointage() — POINTAGE OUVERT (DÉPART → clôture)
    // ======================================================

    @Test
    void should_update_existing_pointage() {

        Pointage existing = new Pointage();
        existing.setHeureArrive("08:00");

        when(dossierEmployeRepository.findByAgentId("1234"))
                .thenReturn(Optional.of(agent("1234", "Dakar")));
        when(pointageRepository.findFirstByCodeSecretAndDateAndHeureDepartIsNullOrderByTimestampDesc(
                eq("1234"), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pointage result = service.enregistrerPointage("1234", "DEV-1", 14.717, -17.467);

        assertNotNull(result);
        assertEquals("TERMINÉ", result.getStatus());
        assertNotNull(result.getHeureDepart());
        assertNotNull(result.getDuree());
        verify(pointageRepository).save(existing);
    }

    // ======================================================
    // 🧪 enregistrerPointage() — MULTI-SITE : nouveau pointage après clôture du précédent
    // ======================================================

    @Test
    void should_create_second_pointage_after_previous_closed() {

        when(dossierEmployeRepository.findByAgentId("1234"))
                .thenReturn(Optional.of(agent("1234", "Site B")));
        // Aucun pointage ouvert (le précédent est déjà clôturé) → nouvelle arrivée
        when(pointageRepository.findFirstByCodeSecretAndDateAndHeureDepartIsNullOrderByTimestampDesc(
                eq("1234"), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pointage result = service.enregistrerPointage("1234", "DEV-1", 14.717, -17.467);

        assertNotNull(result);
        assertEquals("EN COURS...", result.getStatus());
        assertNull(result.getHeureDepart());
        assertNotNull(result.getHeureArrive());
        verify(pointageRepository).save(any());
    }

    // ======================================================
    // 🧪 enregistrerPointage() — AGENT NOT FOUND → 404 (IllegalArgumentException)
    // ======================================================

    @Test
    void should_throw_exception_when_agent_not_found() {

        when(dossierEmployeRepository.findByAgentId("1234")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(IllegalArgumentException.class,
                () -> service.enregistrerPointage("1234", "DEV-1", 14.717, -17.467));

        assertTrue(ex.getMessage().contains("Employé introuvable"));
    }
}
