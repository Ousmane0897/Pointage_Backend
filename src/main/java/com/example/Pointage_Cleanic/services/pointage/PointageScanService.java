package com.example.Pointage_Cleanic.services.pointage;

import com.example.Pointage_Cleanic.Enum.pointage.ModeHorsAffectation;
import com.example.Pointage_Cleanic.entities.pointage.ParametresPointage;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.terrain.CoordonneesGps;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.PointageRefuseException;
import com.example.Pointage_Cleanic.services.terrain.HaversineService;
import com.example.Pointage_Cleanic.util.RattachementSiteUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import static com.example.Pointage_Cleanic.services.terrain.TerrainConstantes.PRECISION_GPS_ANOMALIE_M;
import static com.example.Pointage_Cleanic.services.terrain.TerrainConstantes.RAYON_TOLERANCE_DEFAUT_M;

/**
 * Contrôle d'un scan de QR code d'agence, <b>sans aucune écriture</b>.
 * <p>
 * C'est ce qui permet à {@code PointageServices} de tout valider <b>avant</b> la clôture
 * atomique du pointage ouvert : une sortie refusée laisse le pointage ouvert.
 * <p>
 * Le résultat ne dépend pas du moment (entrée ou sortie) : chaque scan est jugé contre
 * l'agence que <b>son</b> QR désigne, jamais contre l'agence d'entrée. Seul le libellé
 * des anomalies est suffixé par l'appelant, une fois le moment connu par l'écriture.
 */
@Service
@RequiredArgsConstructor
public class PointageScanService {

    private final AgenceQrService agenceQrService;
    private final ParametresPointageService parametresPointageService;
    private final HaversineService haversineService;
    private final Clock clock;

    /**
     * @return vide ⇒ aucun QR fourni et la transition l'autorise : flux historique, sans
     *         agence. Sinon, le scan accepté.
     * @throws PointageRefuseException tout motif de refus (→ 422, message affiché à l'agent)
     */
    public Optional<ScanAgence> evaluer(DossierEmploye agent, String qrPayload,
                                        Double latitude, Double longitude, Double precisionMetres) {
        ParametresPointage parametres = parametresPointageService.getOrCreate();

        if (qrPayload == null || qrPayload.isBlank()) {
            if (ParametresPointageService.qrObligatoire(parametres)) {
                throw new PointageRefuseException("Scannez le QR code de votre agence pour pointer.");
            }
            return Optional.empty();
        }

        if (!AgenceQrService.prefixeReconnu(qrPayload)) {
            throw new PointageRefuseException("QR code non reconnu.");
        }
        // Agence inactive : même message qu'un jeton inconnu, son QR ne doit plus servir.
        SiteClient agence = agenceQrService.resoudre(qrPayload)
                .filter(SiteClient::isActif)
                .orElseThrow(() -> new PointageRefuseException("QR code invalide ou expiré."));

        CoordonneesGps coord = agence.getCoordonnees();
        if (coord == null || coord.getLatitude() == null || coord.getLongitude() == null) {
            throw new PointageRefuseException(
                    "L'agence " + agence.getNom() + " n'est pas géolocalisée : contactez votre responsable.");
        }
        if (latitude == null || longitude == null) {
            throw new PointageRefuseException(
                    "Position GPS requise pour pointer à " + agence.getNom() + ". Activez la localisation.");
        }

        double distance = Math.round(haversineService.distanceM(
                latitude, longitude, coord.getLatitude(), coord.getLongitude()));
        int rayon = agence.getRayonToleranceM() == null || agence.getRayonToleranceM() <= 0
                ? RAYON_TOLERANCE_DEFAUT_M
                : agence.getRayonToleranceM();
        // ⚠ La précision annoncée par l'appareil n'élargit JAMAIS la tolérance : elle est
        // déclarative, et l'élargir offrirait un contournement trivial de la géofence.
        if (distance > rayon) {
            throw new PointageRefuseException(String.format(
                    "Vous êtes hors de la zone de %s (%d m, tolérance %d m).",
                    agence.getNom(), (long) distance, rayon));
        }

        boolean horsAffectation = false;
        String avertissement = null;
        ModeHorsAffectation mode = ParametresPointageService.modeHorsAffectation(parametres);
        if (mode != ModeHorsAffectation.AUTORISER
                && !RattachementSiteUtils.estAffecteAuSite(agent, agence.getNom(), LocalDate.now(clock))) {
            if (mode == ModeHorsAffectation.BLOQUER) {
                throw new PointageRefuseException(
                        "Pointage refusé : " + agence.getNom() + " ne fait pas partie de vos affectations.");
            }
            horsAffectation = true;
            avertissement = agence.getNom()
                    + " ne fait pas partie de vos affectations : le pointage est enregistré et signalé à la RH.";
        }

        boolean precisionFaible = precisionMetres != null && precisionMetres > PRECISION_GPS_ANOMALIE_M;

        return Optional.of(new ScanAgence(agence, latitude, longitude, precisionMetres,
                distance, horsAffectation, precisionFaible, avertissement));
    }

    /** Nom de l'agence d'un payload, pour l'écran d'accueil public. Vide si invalide ou inactive. */
    public Optional<String> nomAgence(String qrPayload) {
        return agenceQrService.resoudre(qrPayload)
                .filter(SiteClient::isActif)
                .map(SiteClient::getNom);
    }

    /** Scan accepté — valeurs à persister sur le pointage, côté entrée ou sortie. */
    public record ScanAgence(
            SiteClient agence,
            Double latitude,
            Double longitude,
            Double precisionMetres,
            double distanceMetres,
            boolean horsAffectation,
            boolean precisionFaible,
            String avertissement) {
    }
}
