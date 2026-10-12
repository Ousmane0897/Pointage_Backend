package com.example.Pointage_Cleanic.services.pointage;

import com.example.Pointage_Cleanic.Dto.pointage.AgenceQrDto;
import com.example.Pointage_Cleanic.entities.terrain.CoordonneesGps;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.PointageAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.example.Pointage_Cleanic.services.terrain.TerrainConstantes.RAYON_TOLERANCE_DEFAUT_M;

/**
 * Administration des QR codes d'agence : liste (avec jetons) et régénération.
 * <p>
 * ⚠ Garde RH / SUPERADMIN posée <b>ici</b> : le projet n'active pas
 * {@code @EnableMethodSecurity}, et la liste expose les jetons — quiconque la lit peut
 * imprimer un QR valide.
 */
@Service
@RequiredArgsConstructor
public class AgenceQrAdminService {

    private final SiteClientRepository siteClientRepository;
    private final AgenceQrService agenceQrService;
    private final ParametresPointageService parametresPointageService;

    /** Toutes les agences (actives et inactives), triées par nom. */
    public List<AgenceQrDto> lister() {
        exigerAdministrateur();
        return siteClientRepository.findAll(Sort.by("nom").ascending()).stream()
                .map(this::toDto)
                .toList();
    }

    /** Nouveau jeton : l'ancien QR imprimé cesse immédiatement de fonctionner. */
    public AgenceQrDto regenerer(String siteId) {
        exigerAdministrateur();
        return toDto(agenceQrService.regenerer(siteId));
    }

    private void exigerAdministrateur() {
        if (!parametresPointageService.estAdministrateur()) {
            throw new PointageAccesRefuseException(
                    "Seuls les profils RH et super-administrateur peuvent gérer les QR codes des agences.");
        }
    }

    private AgenceQrDto toDto(SiteClient s) {
        CoordonneesGps c = s.getCoordonnees();
        boolean geolocalise = c != null && c.getLatitude() != null && c.getLongitude() != null;
        return AgenceQrDto.builder()
                .siteId(s.getId())
                .code(s.getCode())
                .nom(s.getNom())
                .actif(s.isActif())
                .geolocalise(geolocalise)
                .latitude(geolocalise ? c.getLatitude() : null)
                .longitude(geolocalise ? c.getLongitude() : null)
                .rayonToleranceM(s.getRayonToleranceM() == null || s.getRayonToleranceM() <= 0
                        ? RAYON_TOLERANCE_DEFAUT_M : s.getRayonToleranceM())
                .payload(agenceQrService.payload(s))
                .url(agenceQrService.url(s))
                .qrVersion(s.getQrVersion())
                .qrGenereLe(s.getQrGenereLe())
                .build();
    }
}
