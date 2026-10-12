package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.pointage.PointageResponse;
import com.example.Pointage_Cleanic.Enum.pointage.ModeHorsAffectation;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.entities.pointage.ParametresPointage;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.terrain.CoordonneesGps;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.PointageRefuseException;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import com.example.Pointage_Cleanic.services.pointage.AgenceQrService;
import com.example.Pointage_Cleanic.services.pointage.ParametresPointageService;
import com.example.Pointage_Cleanic.services.pointage.PointageScanService;
import com.example.Pointage_Cleanic.services.terrain.HaversineService;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pointage kiosque avec QR code d'agence, de bout en bout côté service : le contrôle
 * (QR, géofence, affectation) est réel, seule la persistance est simulée.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PointageServicesQrTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Africa/Dakar"));
    private static final double M = 1d / 111_195d;
    private static final double LAT_A = 14.7000;
    private static final double LAT_B = 14.7200; // ≈ 2,2 km de A
    private static final double LNG = -17.4500;

    @Mock private MongoTemplate mongoTemplate;
    @Mock private PointageRepository pointageRepository;
    @Mock private DossierEmployeRepository dossierEmployeRepository;
    @Mock private SiteClientRepository siteClientRepository;
    @Mock private ParametresPointageService parametresPointageService;

    private final List<SiteClient> agences = new ArrayList<>();
    private ParametresPointage parametres;
    private AgenceQrService agenceQrService;
    private PointageServices service;
    private SiteClient agenceA;
    private SiteClient agenceB;

    @BeforeEach
    void setUp() {
        agenceQrService = new AgenceQrService(siteClientRepository, CLOCK);
        PointageScanService scan = new PointageScanService(
                agenceQrService, parametresPointageService, new HaversineService(), CLOCK);
        // GeocodingService nul : l'appel est protégé par un try/catch (cf. PointageServicesTest).
        service = new PointageServices(mongoTemplate, pointageRepository, null, dossierEmployeRepository, scan);

        parametres = ParametresPointage.builder()
                .horsAffectation(ModeHorsAffectation.AVERTIR).qrObligatoire(false).build();
        when(parametresPointageService.getOrCreate()).thenAnswer(inv -> parametres);

        when(siteClientRepository.findByQrToken(anyString())).thenAnswer(inv -> agences.stream()
                .filter(s -> inv.getArgument(0).equals(s.getQrToken())).findFirst());
        when(siteClientRepository.findById(anyString())).thenAnswer(inv -> agences.stream()
                .filter(s -> inv.getArgument(0).equals(s.getId())).findFirst());
        when(siteClientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pointageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        agenceA = agence("A", "SGS Mermoz", LAT_A);
        agenceB = agence("B", "BGFI Tann", LAT_B);

        // Agent affecté aux DEUX agences : arriver à l'une et repartir de l'autre est normal.
        DossierEmploye agent = new DossierEmploye();
        agent.setAgentId("1234");
        agent.setPrenom("Awa");
        agent.setNom("Ndiaye");
        agent.setSiteAffecte("SGS Mermoz - BGFI Tann");
        when(dossierEmployeRepository.findByAgentId("1234")).thenReturn(Optional.of(agent));
    }

    private SiteClient agence(String id, String nom, double lat) {
        SiteClient s = SiteClient.builder()
                .id(id).code(id).nom(nom).actif(true)
                .coordonnees(new CoordonneesGps(lat, LNG)).rayonToleranceM(100)
                .build();
        agenceQrService.assurerToken(s);
        agences.add(s);
        return s;
    }

    private Pointage ouvertEn(String agenceId) {
        Pointage p = new Pointage();
        p.setId("p-open");
        p.setCodeSecret("1234");
        p.setHeureArrive("08:00");
        p.setAgenceId(agenceId);
        return p;
    }

    private void aucunPointageOuvert() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Pointage.class))).thenReturn(null);
    }

    private void pointageOuvert(Pointage p) {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Pointage.class))).thenReturn(p);
    }

    private Document updateDeCloture() {
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(any(Query.class), captor.capture(),
                any(FindAndModifyOptions.class), eq(Pointage.class));
        return captor.getValue().getUpdateObject();
    }

    // ─── Entrée ─────────────────────────────────────────────────────────────

    @Test
    void entree_enregistre_l_agence_la_position_et_la_distance() {
        aucunPointageOuvert();

        PointageResponse r = service.pointer("1234", "DEV-1", LAT_A + 20 * M, LNG, 8.0,
                agenceQrService.payload(agenceA));

        assertEquals("ENTREE", r.getType());
        assertEquals("A", r.getAgenceId());
        assertEquals("SGS Mermoz", r.getAgenceNom());
        assertEquals(20, r.getDistanceMetres(), 1);
        assertNull(r.getAvertissement());

        Pointage p = r.getPointage();
        assertEquals("A", p.getAgenceId());
        assertEquals(8.0, p.getPrecisionMetres());
        assertNull(p.getAnomalies());
        assertNull(p.getAgenceIdSortie());
    }

    @Test
    void entree_hors_affectation_est_marquee_entree() {
        aucunPointageOuvert();
        SiteClient c = agence("C", "SGS Yoff", 14.75);

        PointageResponse r = service.pointer("1234", "DEV-1", 14.75, LNG, null,
                agenceQrService.payload(c));

        assertEquals(List.of("HORS_AFFECTATION_ENTREE"), r.getPointage().getAnomalies());
        assertNotNull(r.getAvertissement());
    }

    // ─── Sortie ─────────────────────────────────────────────────────────────

    @Test
    void entree_en_a_puis_sortie_en_b_est_acceptee_sans_anomalie() {
        pointageOuvert(ouvertEn("A"));

        PointageResponse r = service.pointer("1234", "DEV-1", LAT_B + 10 * M, LNG, null,
                agenceQrService.payload(agenceB));

        assertEquals("SORTIE", r.getType());
        // L'agence de la réponse est celle du scan courant : la sortie.
        assertEquals("B", r.getAgenceId());
        assertEquals("BGFI Tann", r.getAgenceNom());
        assertNull(r.getAvertissement());

        Pointage p = r.getPointage();
        assertEquals("A", p.getAgenceId());
        assertEquals("B", p.getAgenceIdSortie());
        assertNull(p.getAnomalies());

        Document update = updateDeCloture();
        Document set = (Document) update.get("$set");
        assertEquals("B", set.get("agenceIdSortie"));
        assertEquals(10, (Double) set.get("distanceMetresSortie"), 1);
        assertNull(update.get("$push"), "aucune anomalie ne doit être poussée");
    }

    @Test
    void la_geofence_de_sortie_vise_l_agence_scannee_et_non_celle_d_entree() {
        pointageOuvert(ouvertEn("A"));

        // Position sur A, mais QR de B : refusé, même si l'agent est dans le rayon de A.
        PointageRefuseException ex = assertThrows(PointageRefuseException.class, () ->
                service.pointer("1234", "DEV-1", LAT_A, LNG, null, agenceQrService.payload(agenceB)));
        assertTrue(ex.getMessage().contains("BGFI Tann"));
    }

    @Test
    void sortie_refusee_hors_zone_laisse_le_pointage_ouvert() {
        pointageOuvert(ouvertEn("A"));

        assertThrows(PointageRefuseException.class, () ->
                service.pointer("1234", "DEV-1", LAT_A - 500 * M, LNG, null, agenceQrService.payload(agenceA)));

        // Aucune écriture : le pointage n'est pas clôturé (heureDepart reste null en base).
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(Pointage.class));
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Pointage.class));
        verify(pointageRepository, never()).save(any());
    }

    @Test
    void jeton_regenere_pendant_un_pointage_ouvert_la_sortie_avec_le_nouveau_qr_passe() {
        aucunPointageOuvert();
        String ancienQr = agenceQrService.payload(agenceA);
        service.pointer("1234", "DEV-1", LAT_A, LNG, null, ancienQr);

        agenceQrService.regenerer("A");
        pointageOuvert(ouvertEn("A"));

        // L'ancien QR imprimé ne fonctionne plus…
        assertThrows(PointageRefuseException.class, () ->
                service.pointer("1234", "DEV-1", LAT_A, LNG, null, ancienQr));
        // …le nouveau clôture le pointage ouvert sous l'ancien jeton.
        PointageResponse r = service.pointer("1234", "DEV-1", LAT_A, LNG, null,
                agenceQrService.payload(agenceA));
        assertEquals("SORTIE", r.getType());
        assertEquals("A", r.getPointage().getAgenceIdSortie());
    }

    @Test
    void sortie_hors_affectation_et_imprecise_pousse_les_anomalies_de_sortie() {
        Pointage ouvert = ouvertEn("A");
        ouvert.setAnomalies(new ArrayList<>(List.of("PRECISION_FAIBLE_ENTREE")));
        pointageOuvert(ouvert);
        SiteClient c = agence("C", "SGS Yoff", 14.75);

        PointageResponse r = service.pointer("1234", "DEV-1", 14.75, LNG, 250.0,
                agenceQrService.payload(c));

        assertEquals(List.of("PRECISION_FAIBLE_ENTREE", "HORS_AFFECTATION_SORTIE", "PRECISION_FAIBLE_SORTIE"),
                r.getPointage().getAnomalies());
        assertNotNull(updateDeCloture().get("$push"));
    }

    // ─── Transition ─────────────────────────────────────────────────────────

    @Test
    void sans_qr_le_flux_historique_est_inchange() {
        aucunPointageOuvert();

        PointageResponse r = service.pointer("1234", "DEV-1", LAT_A, LNG, null, null);

        assertEquals("ENTREE", r.getType());
        assertNull(r.getAgenceId());
        assertNull(r.getPointage().getAgenceId());
        assertArrayEquals(new String[]{"SGS Mermoz", "BGFI Tann"}, r.getPointage().getSite());
    }

    @Test
    void sans_qr_et_qr_obligatoire_rien_n_est_ecrit() {
        parametres.setQrObligatoire(true);

        assertThrows(PointageRefuseException.class, () ->
                service.pointer("1234", "DEV-1", LAT_A, LNG, null, null));
        verifyNoInteractions(mongoTemplate);
        verify(pointageRepository, never()).save(any());
    }
}
