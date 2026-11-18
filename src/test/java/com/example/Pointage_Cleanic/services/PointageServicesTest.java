package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.PointageTropFrequentException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

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
    // 🧪 Garde anti-triche — refus AVANT toute écriture
    // ======================================================

    @Test
    void telephone_d_un_autre_agent_refuse_sans_rien_ecrire() {

        when(dossierEmployeRepository.findByAgentId("1234"))
                .thenReturn(Optional.of(agent("1234", "Site B")));
        // 1er exists = téléphone partagé
        when(mongoTemplate.exists(any(Query.class), eq(Pointage.class))).thenReturn(true);

        PointageTropFrequentException ex = assertThrows(PointageTropFrequentException.class,
                () -> service.enregistrerPointage("1234", "DEV-1", null, null));

        assertEquals(PointageServices.MESSAGE_TELEPHONE_PARTAGE, ex.getMessage());
        verify(mongoTemplate, never()).findAndModify(any(), any(), any(FindAndModifyOptions.class), eq(Pointage.class));
        verify(pointageRepository, never()).save(any());
    }

    @Test
    void double_envoi_du_meme_agent_refuse_sans_rien_ecrire() {

        when(dossierEmployeRepository.findByAgentId("1234"))
                .thenReturn(Optional.of(agent("1234", "Site B")));
        // téléphone libre, mais l'agent vient de pointer
        when(mongoTemplate.exists(any(Query.class), eq(Pointage.class))).thenReturn(false, true);

        PointageTropFrequentException ex = assertThrows(PointageTropFrequentException.class,
                () -> service.enregistrerPointage("1234", "DEV-1", null, null));

        assertEquals(PointageServices.MESSAGE_DOUBLE_ENVOI, ex.getMessage());
        verify(pointageRepository, never()).save(any());
    }

    @Test
    void la_sortie_trace_le_telephone_utilise() {

        Pointage existing = new Pointage();
        existing.setId("p-open");
        existing.setHeureArrive("08:00");
        when(dossierEmployeRepository.findByAgentId("1234"))
                .thenReturn(Optional.of(agent("1234", "Dakar")));
        when(mongoTemplate.findAndModify(any(), any(),
                any(FindAndModifyOptions.class), eq(Pointage.class)))
                .thenReturn(existing);

        Pointage result = service.enregistrerPointage("1234", "DEV-9", null, null);

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(), update.capture(),
                any(FindAndModifyOptions.class), eq(Pointage.class));
        org.bson.Document set = (org.bson.Document) update.getValue().getUpdateObject().get("$set");
        assertEquals("DEV-9", set.get("deviceIdSortie"));
        assertNotNull(set.get("timestampSortie"));
        assertEquals("DEV-9", result.getDeviceIdSortie());
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
        // Aucun pointage ouvert : la clôture atomique ne matche rien → arrivée.
        when(mongoTemplate.findAndModify(any(), any(),
                any(FindAndModifyOptions.class), eq(Pointage.class)))
                .thenReturn(null);
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
        existing.setId("p-open");
        existing.setHeureArrive("08:00");

        when(dossierEmployeRepository.findByAgentId("1234"))
                .thenReturn(Optional.of(agent("1234", "Dakar")));
        // Clôture atomique : findAndModify renvoie l'état pré-update (heureArrive).
        when(mongoTemplate.findAndModify(any(), any(),
                any(FindAndModifyOptions.class), eq(Pointage.class)))
                .thenReturn(existing);

        Pointage result = service.enregistrerPointage("1234", "DEV-1", 14.717, -17.467);

        assertNotNull(result);
        assertEquals("TERMINÉ", result.getStatus());
        assertNotNull(result.getHeureDepart());
        assertNotNull(result.getDuree());
        // Durée renseignée par un updateFirst ciblé ; pas de save() repository.
        verify(mongoTemplate).updateFirst(any(), any(), eq(Pointage.class));
        verify(pointageRepository, never()).save(any());
    }

    // ======================================================
    // 🧪 enregistrerPointage() — MULTI-SITE : nouveau pointage après clôture du précédent
    // ======================================================

    @Test
    void should_create_second_pointage_after_previous_closed() {

        when(dossierEmployeRepository.findByAgentId("1234"))
                .thenReturn(Optional.of(agent("1234", "Site B")));
        // Aucun pointage ouvert (le précédent est déjà clôturé) → nouvelle arrivée
        when(mongoTemplate.findAndModify(any(), any(),
                any(FindAndModifyOptions.class), eq(Pointage.class)))
                .thenReturn(null);
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Pointage result = service.enregistrerPointage("1234", "DEV-1", 14.717, -17.467);

        assertNotNull(result);
        assertEquals("EN COURS...", result.getStatus());
        assertNull(result.getHeureDepart());
        assertNotNull(result.getHeureArrive());
        verify(pointageRepository).save(any());
    }

    // ======================================================
    // 🧪 enregistrerPointage() — AGENT NOT FOUND → 404 (ResourceNotFoundException)
    // ======================================================

    @Test
    void should_throw_exception_when_agent_not_found() {

        when(dossierEmployeRepository.findByAgentId("1234")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.enregistrerPointage("1234", "DEV-1", 14.717, -17.467));

        assertTrue(ex.getMessage().contains("Employé introuvable"));
    }
}
