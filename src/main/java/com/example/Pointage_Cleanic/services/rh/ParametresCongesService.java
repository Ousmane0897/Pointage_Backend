package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.PalierAncienneteCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.ParametresCongesDto;
import com.example.Pointage_Cleanic.entities.rh.PalierAncienneteConge;
import com.example.Pointage_Cleanic.entities.rh.ParametresConges;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.rh.ParametresCongesRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Barème des droits à congés — document singleton, get-or-create paresseux.
 *
 * <p>Le patron est celui de {@code ParametresProductionChimieService} et <b>non</b> celui de
 * {@code ParametresPaieService} : ce dernier lève un 404 quand son DataLoader n'a pas tourné,
 * ce qui rendrait le <i>calcul du solde</i> impossible sur une base neuve. Ici, le barème
 * légal est semé à la première lecture.
 */
@Service
@RequiredArgsConstructor
public class ParametresCongesService {

    private final ParametresCongesRepository repository;
    private final CongeIdentiteService identite;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Valeur de <b>semis</b> du taux mensuel, et rien d'autre : une fois le document créé,
     * c'est lui qui fait foi et cette propriété n'est plus relue. C'est la raison pour
     * laquelle {@link CongeAcquisCalculator} ne la porte plus.
     */
    @Value("${app.conges.jours-acquis-par-mois:2}")
    private int joursAcquisParMoisSemis;

    public ParametresCongesDto getParametres() {
        return toDto(getOrCreate());
    }

    /**
     * Modification du barème, réservée à la RH et au super-admin.
     *
     * <p>⚠ La garde est posée <b>ici</b> et non par une annotation : le projet n'active pas
     * {@code @EnableMethodSecurity}, et {@code SecurityConfig} se limite à
     * {@code .anyRequest().authenticated()} — un {@code @PreAuthorize} serait ignoré en
     * silence. C'est aussi la façon dont tout le module congés porte ses habilitations.
     *
     * <p>Le patch est <b>champ par champ</b> : un {@code null} laisse la valeur en base
     * inchangée. Sans cela, un client partiel effacerait les paliers d'ancienneté.
     */
    public ParametresCongesDto updateParametres(ParametresCongesDto dto) {
        if (!identite.estRh() && !identite.estSuperAdmin()) {
            throw new CongeAccesRefuseException(
                    "Seuls les profils RH et super-administrateur peuvent modifier le barème de congés.");
        }
        validerPatch(dto);

        ParametresConges entity = getOrCreate();
        if (dto.getJoursAcquisParMois() != null) entity.setJoursAcquisParMois(dto.getJoursAcquisParMois());
        if (dto.getSupplementEnfantsActif() != null) entity.setSupplementEnfantsActif(dto.getSupplementEnfantsActif());
        if (dto.getJoursParEnfant() != null) entity.setJoursParEnfant(dto.getJoursParEnfant());
        if (dto.getAgeMaxEnfant() != null) entity.setAgeMaxEnfant(dto.getAgeMaxEnfant());
        if (dto.getReserverAuxMeres() != null) entity.setReserverAuxMeres(dto.getReserverAuxMeres());
        if (dto.getSupplementAncienneteActif() != null) {
            entity.setSupplementAncienneteActif(dto.getSupplementAncienneteActif());
        }
        if (dto.getProratiserSupplements() != null) {
            entity.setProratiserSupplements(dto.getProratiserSupplements());
        }
        if (dto.getPaliersAnciennete() != null) {
            entity.setPaliersAnciennete(trierPaliers(dto.getPaliersAnciennete()));
        }
        // Le plafond est le seul champ effaçable : « null = inchangé » interdirait de le
        // retirer une fois posé. Un plafond de 0 signifierait « aucun jour », ce qui n'est
        // pas la même chose — d'où la sentinelle négative plutôt qu'un 0.
        if (dto.getPlafondJoursEnfants() != null) {
            entity.setPlafondJoursEnfants(
                    dto.getPlafondJoursEnfants() < 0 ? null : dto.getPlafondJoursEnfants());
        }

        entity.setDateModification(LocalDateTime.now());
        entity.setModifieParId(currentUserProvider.currentUserId());
        entity.setModifieParNom(currentUserProvider.currentUserNom());
        return toDto(repository.save(entity));
    }

    /**
     * Snapshot immuable du barème pour le calcul des droits.
     *
     * <p>⚠ <b>Une seule fois par méthode publique appelante</b> — c'est la même discipline que
     * {@code PerimetreConges} : {@code getSoldes()} calcule N soldes, une lecture par employé
     * serait N requêtes Mongo pour une valeur identique.
     */
    public BaremeConges baremeCourant() {
        return toBareme(getOrCreate());
    }

    /** Entité singleton, semée au barème légal si absente. */
    public ParametresConges getOrCreate() {
        return repository.findFirstByOrderByIdAsc().orElseGet(() -> repository.save(defauts()));
    }

    private ParametresConges defauts() {
        BaremeConges legal = BaremeConges.defaut();
        return ParametresConges.builder()
                .joursAcquisParMois(joursAcquisParMoisSemis)
                .supplementEnfantsActif(legal.supplementEnfantsActif())
                .joursParEnfant(legal.joursParEnfant())
                .ageMaxEnfant(legal.ageMaxEnfant())
                .reserverAuxMeres(legal.reserverAuxMeres())
                .plafondJoursEnfants(legal.plafondJoursEnfants())
                .supplementAncienneteActif(legal.supplementAncienneteActif())
                .paliersAnciennete(legal.paliersAnciennete().stream()
                        .map(p -> PalierAncienneteConge.builder()
                                .anneesAnciennete(p.anneesAnciennete())
                                .joursSupplementaires(p.joursSupplementaires())
                                .build())
                        .toList())
                .proratiserSupplements(legal.proratiserSupplements())
                .dateModification(LocalDateTime.now())
                .modifieParNom("system")
                .build();
    }

    private void validerPatch(ParametresCongesDto dto) {
        if (dto.getAgeMaxEnfant() != null && dto.getAgeMaxEnfant() > 30) {
            throw new IllegalArgumentException("ageMaxEnfant doit être compris entre 0 et 30");
        }
        List<PalierAncienneteCongeDto> paliers = dto.getPaliersAnciennete();
        if (paliers == null) {
            return;
        }
        Set<Integer> vues = new HashSet<>();
        for (PalierAncienneteCongeDto p : paliers) {
            if (p == null || p.getAnneesAnciennete() == null || p.getJoursSupplementaires() == null) {
                throw new IllegalArgumentException(
                        "Chaque palier doit porter une ancienneté et un nombre de jours");
            }
            if (!vues.add(p.getAnneesAnciennete())) {
                throw new IllegalArgumentException(
                        "Deux paliers ne peuvent pas partager la même ancienneté : "
                                + p.getAnneesAnciennete() + " ans");
            }
        }
    }

    /**
     * Tri croissant appliqué serveur. Le calcul retient le {@code max} et ne dépend donc pas
     * de l'ordre ; c'est l'affichage du barème qui y gagne, quel que soit le client.
     */
    private List<PalierAncienneteConge> trierPaliers(List<PalierAncienneteCongeDto> paliers) {
        List<PalierAncienneteConge> tries = new ArrayList<>(paliers.stream()
                .map(p -> PalierAncienneteConge.builder()
                        .anneesAnciennete(p.getAnneesAnciennete())
                        .joursSupplementaires(p.getJoursSupplementaires())
                        .build())
                .toList());
        tries.sort(Comparator.comparingInt(PalierAncienneteConge::getAnneesAnciennete));
        return tries;
    }

    /**
     * Conversion vers le snapshot de calcul. Chaque champ nul retombe sur le barème légal :
     * un document semé par une version antérieure ne doit jamais produire un droit à zéro
     * par simple absence de champ.
     */
    private BaremeConges toBareme(ParametresConges e) {
        BaremeConges legal = BaremeConges.defaut();
        List<BaremeConges.Palier> paliers = e.getPaliersAnciennete() == null
                ? legal.paliersAnciennete()
                : e.getPaliersAnciennete().stream()
                        .filter(p -> p != null && p.getAnneesAnciennete() != null
                                && p.getJoursSupplementaires() != null)
                        .map(p -> new BaremeConges.Palier(p.getAnneesAnciennete(), p.getJoursSupplementaires()))
                        .toList();

        return new BaremeConges(
                valeur(e.getJoursAcquisParMois(), legal.joursAcquisParMois()),
                valeur(e.getSupplementEnfantsActif(), legal.supplementEnfantsActif()),
                valeur(e.getJoursParEnfant(), legal.joursParEnfant()),
                valeur(e.getAgeMaxEnfant(), legal.ageMaxEnfant()),
                valeur(e.getReserverAuxMeres(), legal.reserverAuxMeres()),
                e.getPlafondJoursEnfants(),
                valeur(e.getSupplementAncienneteActif(), legal.supplementAncienneteActif()),
                paliers,
                valeur(e.getProratiserSupplements(), legal.proratiserSupplements()));
    }

    private static int valeur(Integer v, int defaut) {
        return v == null ? defaut : v;
    }

    private static boolean valeur(Boolean v, boolean defaut) {
        return v == null ? defaut : v;
    }

    private ParametresCongesDto toDto(ParametresConges e) {
        return ParametresCongesDto.builder()
                .id(e.getId())
                .joursAcquisParMois(e.getJoursAcquisParMois())
                .supplementEnfantsActif(e.getSupplementEnfantsActif())
                .joursParEnfant(e.getJoursParEnfant())
                .ageMaxEnfant(e.getAgeMaxEnfant())
                .reserverAuxMeres(e.getReserverAuxMeres())
                .plafondJoursEnfants(e.getPlafondJoursEnfants())
                .supplementAncienneteActif(e.getSupplementAncienneteActif())
                .paliersAnciennete(e.getPaliersAnciennete() == null ? List.of()
                        : e.getPaliersAnciennete().stream()
                                .map(p -> PalierAncienneteCongeDto.builder()
                                        .anneesAnciennete(p.getAnneesAnciennete())
                                        .joursSupplementaires(p.getJoursSupplementaires())
                                        .build())
                                .toList())
                .proratiserSupplements(e.getProratiserSupplements())
                .dateModification(e.getDateModification())
                .modifieParId(e.getModifieParId())
                .modifieParNom(e.getModifieParNom())
                .build();
    }
}
