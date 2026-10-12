package com.example.Pointage_Cleanic.services.pointage;

import com.example.Pointage_Cleanic.Enum.pointage.ModeHorsAffectation;
import com.example.Pointage_Cleanic.entities.pointage.ParametresPointage;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.terrain.CoordonneesGps;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.PointageRefuseException;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import com.example.Pointage_Cleanic.services.pointage.PointageScanService.ScanAgence;
import com.example.Pointage_Cleanic.services.terrain.HaversineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Contrôle du scan QR d'agence. {@link AgenceQrService} et {@link HaversineService} sont
 * réels ; seul le dépôt des agences est simulé, par une petite base en mémoire qui
 * résout un jeton contre le jeton <b>courant</b> de chaque agence — c'est ce qui rend
 * le test de régénération significatif.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PointageScanServiceTest {

    /** Dakar, date figée : le résultat ne dépend pas du jour d'exécution. */
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-12T09:00:00Z"), ZoneId.of("Africa/Dakar"));

    /** ≈ 1 m en degrés de latitude. */
    private static final double M = 1d / 111_195d;

    private static final double LAT_A = 14.7000;
    private static final double LNG = -17.4500;

    @Mock
    private SiteClientRepository siteClientRepository;
    @Mock
    private ParametresPointageService parametresPointageService;

    private final List<SiteClient> agences = new ArrayList<>();
    private ParametresPointage parametres;
    private AgenceQrService agenceQrService;
    private PointageScanService service;

    private SiteClient agenceA;
    private SiteClient agenceB;

    @BeforeEach
    void setUp() {
        agenceQrService = new AgenceQrService(siteClientRepository, CLOCK);
        service = new PointageScanService(agenceQrService, parametresPointageService, new HaversineService(), CLOCK);

        parametres = ParametresPointage.builder()
                .horsAffectation(ModeHorsAffectation.AVERTIR).qrObligatoire(false).build();
        when(parametresPointageService.getOrCreate()).thenAnswer(inv -> parametres);

        when(siteClientRepository.findByQrToken(anyString())).thenAnswer(inv -> agences.stream()
                .filter(s -> inv.getArgument(0).equals(s.getQrToken())).findFirst());
        when(siteClientRepository.findById(anyString())).thenAnswer(inv -> agences.stream()
                .filter(s -> inv.getArgument(0).equals(s.getId())).findFirst());
        when(siteClientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Deux agences à 60 m l'une de l'autre, rayon 100 m : un point entre les deux est
        // dans le rayon des DEUX. C'est le cas SGS Mermoz / BGFI Tann.
        agenceA = agence("A", "SGS Mermoz", LAT_A);
        agenceB = agence("B", "BGFI Tann", LAT_A + 60 * M);
    }

    private SiteClient agence(String id, String nom, double lat) {
        SiteClient s = SiteClient.builder()
                .id(id).code(id).nom(nom).actif(true)
                .coordonnees(new CoordonneesGps(lat, LNG))
                .rayonToleranceM(100)
                .build();
        agenceQrService.assurerToken(s);
        agences.add(s);
        return s;
    }

    private static DossierEmploye agentAffecteA() {
        DossierEmploye d = new DossierEmploye();
        d.setAgentId("1234");
        d.setSiteAffecte("SGS Mermoz");
        return d;
    }

    private String qr(SiteClient s) {
        return agenceQrService.payload(s);
    }

    private ScanAgence scanner(String payload, double lat, Double precision) {
        return service.evaluer(agentAffecteA(), payload, lat, LNG, precision).orElseThrow();
    }

    private void assertRefus(String messageAttendu, Runnable scan) {
        PointageRefuseException ex = assertThrows(PointageRefuseException.class, scan::run);
        assertTrue(ex.getMessage().contains(messageAttendu), ex.getMessage());
    }

    // ─── Transition : pas de QR ─────────────────────────────────────────────

    @Test
    void sans_payload_et_qr_facultatif_suit_le_flux_historique() {
        assertTrue(service.evaluer(agentAffecteA(), null, LAT_A, LNG, null).isEmpty());
        assertTrue(service.evaluer(agentAffecteA(), "  ", null, null, null).isEmpty());
    }

    @Test
    void sans_payload_et_qr_obligatoire_est_refuse() {
        parametres.setQrObligatoire(true);
        assertRefus("Scannez le QR code",
                () -> service.evaluer(agentAffecteA(), null, LAT_A, LNG, null));
    }

    // ─── Validité du QR ─────────────────────────────────────────────────────

    @Test
    void payload_sans_le_prefixe_est_non_reconnu() {
        assertRefus("QR code non reconnu", () -> scanner("https://exemple.com", LAT_A, null));
        assertRefus("QR code non reconnu", () -> scanner(agenceA.getQrToken(), LAT_A, null));
    }

    @Test
    void jeton_inconnu_est_invalide() {
        assertRefus("QR code invalide ou expiré", () -> scanner("POINTIC:AG:inconnu", LAT_A, null));
        assertRefus("QR code invalide ou expiré", () -> scanner("POINTIC:AG:", LAT_A, null));
    }

    @Test
    void agence_inactive_est_traitee_comme_un_jeton_invalide() {
        agenceA.setActif(false);
        assertRefus("QR code invalide ou expiré", () -> scanner(qr(agenceA), LAT_A, null));
    }

    @Test
    void regeneration_invalide_l_ancien_jeton_et_valide_le_nouveau() {
        String ancien = qr(agenceA);
        agenceQrService.regenerer("A");

        assertEquals(2, agenceA.getQrVersion());
        assertRefus("QR code invalide ou expiré", () -> scanner(ancien, LAT_A, null));
        assertEquals("A", scanner(qr(agenceA), LAT_A, null).agence().getId());
    }

    // ─── Géofence ───────────────────────────────────────────────────────────

    @Test
    void agence_non_geolocalisee_est_refusee() {
        agenceA.setCoordonnees(null);
        assertRefus("SGS Mermoz n'est pas géolocalisée", () -> scanner(qr(agenceA), LAT_A, null));
    }

    @Test
    void position_absente_est_refusee() {
        assertRefus("Position GPS requise",
                () -> service.evaluer(agentAffecteA(), qr(agenceA), null, null, null));
    }

    @Test
    void hors_rayon_est_refuse_avec_le_nom_de_l_agence() {
        assertRefus("hors de la zone de SGS Mermoz",
                () -> scanner(qr(agenceA), LAT_A - 150 * M, null));
    }

    @Test
    void dans_le_rayon_est_accepte_avec_la_distance_mesuree() {
        ScanAgence scan = scanner(qr(agenceA), LAT_A + 40 * M, 15.0);

        assertEquals("A", scan.agence().getId());
        assertEquals(40, scan.distanceMetres(), 1);
        assertFalse(scan.horsAffectation());
        assertFalse(scan.precisionFaible());
        assertNull(scan.avertissement());
    }

    @Test
    void rayon_absent_retombe_sur_le_defaut_de_100_m() {
        agenceA.setRayonToleranceM(null);
        assertEquals("A", scanner(qr(agenceA), LAT_A + 90 * M, null).agence().getId());
        assertRefus("tolérance 100 m", () -> scanner(qr(agenceA), LAT_A - 110 * M, null));
    }

    @Test
    void deux_agences_proches_c_est_le_qr_qui_determine_l_agence() {
        // Point à mi-chemin : dans le rayon de A et de B.
        double milieu = LAT_A + 30 * M;
        parametres.setHorsAffectation(ModeHorsAffectation.AUTORISER);

        assertEquals("A", scanner(qr(agenceA), milieu, null).agence().getId());
        assertEquals("B", scanner(qr(agenceB), milieu, null).agence().getId());
    }

    // ─── Précision GPS ──────────────────────────────────────────────────────

    @Test
    void precision_faible_est_signalee_sans_elargir_la_tolerance() {
        // Dans le rayon : accepté, mais marqué.
        assertTrue(scanner(qr(agenceA), LAT_A + 50 * M, 300.0).precisionFaible());
        // 120 m pour un rayon de 100 m : une précision de 300 m ne repêche rien.
        assertRefus("hors de la zone", () -> scanner(qr(agenceA), LAT_A - 120 * M, 300.0));
        // Seuil exclusif à 100 m.
        assertFalse(scanner(qr(agenceA), LAT_A, 100.0).precisionFaible());
    }

    // ─── POINTAGE_HORS_AFFECTATION ──────────────────────────────────────────

    @Test
    void hors_affectation_bloquer_refuse() {
        parametres.setHorsAffectation(ModeHorsAffectation.BLOQUER);
        assertRefus("BGFI Tann ne fait pas partie de vos affectations",
                () -> scanner(qr(agenceB), LAT_A + 60 * M, null));
        // L'agence d'affectation passe toujours.
        assertEquals("A", scanner(qr(agenceA), LAT_A, null).agence().getId());
    }

    @Test
    void hors_affectation_avertir_accepte_et_signale() {
        parametres.setHorsAffectation(ModeHorsAffectation.AVERTIR);
        ScanAgence scan = scanner(qr(agenceB), LAT_A + 60 * M, null);

        assertEquals("B", scan.agence().getId());
        assertTrue(scan.horsAffectation());
        assertTrue(scan.avertissement().contains("BGFI Tann"));
    }

    @Test
    void hors_affectation_autoriser_ne_controle_rien() {
        parametres.setHorsAffectation(ModeHorsAffectation.AUTORISER);
        ScanAgence scan = scanner(qr(agenceB), LAT_A + 60 * M, null);

        assertFalse(scan.horsAffectation());
        assertNull(scan.avertissement());
    }

    @Test
    void mode_absent_du_document_retombe_sur_avertir() {
        parametres.setHorsAffectation(null);
        assertTrue(scanner(qr(agenceB), LAT_A + 60 * M, null).horsAffectation());
    }

    // ─── Écran d'accueil public ─────────────────────────────────────────────

    @Test
    void nom_agence_ne_resout_que_les_qr_valides_et_actifs() {
        assertEquals(Optional.of("SGS Mermoz"), service.nomAgence(qr(agenceA)));
        assertTrue(service.nomAgence("POINTIC:AG:inconnu").isEmpty());
        assertTrue(service.nomAgence("n'importe quoi").isEmpty());
        agenceB.setActif(false);
        assertTrue(service.nomAgence(qr(agenceB)).isEmpty());
    }
}
