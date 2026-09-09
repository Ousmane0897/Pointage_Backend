package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.JourFerieDto;
import com.example.Pointage_Cleanic.entities.rh.JourFerie;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.exception.JourFerieConflitException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.JourFerieRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Référentiel des jours fériés — CRUD annuel tenu par la RH.
 *
 * <p><b>Lecture ouverte à tout compte authentifié</b> : le calcul des jours ouvrables, des
 * soldes de congés et du pointage centralisé en dépend, y compris pour un agent qui consulte
 * son propre solde. <b>Écriture réservée RH / super-admin.</b>
 *
 * <p>⚠ La garde est posée <b>ici</b> et non par une annotation : le projet n'active pas
 * {@code @EnableMethodSecurity} et {@code SecurityConfig} se limite à
 * {@code .anyRequest().authenticated()} — un {@code @PreAuthorize} serait ignoré en silence.
 * C'est déjà la façon dont {@link ParametresCongesService} porte la sienne.
 */
@Service
@RequiredArgsConstructor
public class JourFerieService {

    private final JourFerieRepository repository;
    private final CongeIdentiteService identite;
    private final CurrentUserProvider currentUserProvider;

    // --- Lecture ------------------------------------------------------------

    /** Fériés d'une année civile, triés par date croissante. */
    public List<JourFerieDto> getParAnnee(int annee) {
        return repository.findByDateBetween(LocalDate.of(annee, 1, 1), LocalDate.of(annee, 12, 31))
                .stream()
                .sorted(Comparator.comparing(JourFerie::getDate))
                .map(this::toDto)
                .toList();
    }

    /**
     * Dates fériées d'une période, prêtes pour le calcul.
     *
     * <p>⚠ <b>Point d'entrée unique des calculs</b> ({@code CalendrierTravailService},
     * décompte des congés, pointage centralisé) : ils appellent cette méthode <b>une fois</b>
     * par méthode publique et font ensuite circuler le {@code Set}. Une lecture par employé
     * serait N requêtes Mongo pour une valeur identique, et deux employés d'un même
     * récapitulatif pourraient être calculés sur des calendriers différents si l'exécution
     * chevauchait une saisie RH.
     *
     * <p>Un {@code Set} et non une {@code List} : le seul usage est {@code contains(date)}, et
     * l'unicité de la date est déjà garantie par l'index — mais un doublon résiduel en base
     * (index créé après coup) ne doit pas faire compter le jour deux fois.
     */
    public Set<LocalDate> datesFeriees(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null || fin.isBefore(debut)) {
            return Set.of();
        }
        return repository.findByDateBetween(debut, fin).stream()
                .map(JourFerie::getDate)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // --- Écriture -----------------------------------------------------------

    public JourFerieDto creer(JourFerieDto dto) {
        exigerDroitEcriture();
        if (repository.existsByDate(dto.getDate())) {
            throw new JourFerieConflitException(
                    "Un jour férié est déjà enregistré au " + dto.getDate() + ".");
        }
        JourFerie entity = JourFerie.builder()
                .date(dto.getDate())
                .libelle(dto.getLibelle().trim())
                // Défauts serveur : un client qui n'envoie pas le champ obtient le cas courant
                // (férié chômé, non reconductible), jamais un null qui ferait planter le tri.
                .chome(dto.getChome() == null || dto.getChome())
                .recurrent(dto.getRecurrent() != null && dto.getRecurrent())
                .build();
        return toDto(repository.save(horodater(entity)));
    }

    public JourFerieDto modifier(String id, JourFerieDto dto) {
        exigerDroitEcriture();
        JourFerie entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jour férié introuvable : " + id));

        // Le conflit ne se pose que si la date change réellement : réenregistrer un férié sans
        // toucher à sa date ne doit pas se heurter à son propre document.
        if (!dto.getDate().equals(entity.getDate()) && repository.existsByDate(dto.getDate())) {
            throw new JourFerieConflitException(
                    "Un jour férié est déjà enregistré au " + dto.getDate() + ".");
        }

        entity.setDate(dto.getDate());
        entity.setLibelle(dto.getLibelle().trim());
        if (dto.getChome() != null) entity.setChome(dto.getChome());
        if (dto.getRecurrent() != null) entity.setRecurrent(dto.getRecurrent());
        return toDto(repository.save(horodater(entity)));
    }

    public void supprimer(String id) {
        exigerDroitEcriture();
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Jour férié introuvable : " + id);
        }
        repository.deleteById(id);
    }

    /**
     * Recopie sur {@code anneeCible} les fériés <b>à date fixe</b> de {@code anneeSource}.
     *
     * <p>⚠ Les fêtes mobiles ({@code recurrent = false}) sont volontairement <b>ignorées</b> :
     * les décaler mécaniquement d'un an produirait de fausses dates que personne ne
     * recouperait — au Sénégal, Korité et Tabaski reculent d'environ onze jours et ne sont
     * annoncées qu'à quelques jours. Elles restent à saisir à la main, et c'est le sens du
     * drapeau {@code recurrent}.
     *
     * <p><b>Idempotent</b> : une date déjà présente sur l'année cible est sautée, jamais
     * écrasée — relancer la duplication ne doit ni dupliquer, ni défaire une correction RH.
     * Le 29 février d'une année bissextile n'a pas d'équivalent l'année suivante : il est
     * sauté plutôt que reporté au 1er mars.
     *
     * @return les fériés effectivement créés
     */
    public List<JourFerieDto> dupliquerAnnee(int anneeSource, int anneeCible) {
        exigerDroitEcriture();
        if (anneeSource == anneeCible) {
            throw new IllegalArgumentException(
                    "L'année source et l'année cible doivent être différentes.");
        }

        List<JourFerie> aCreer = repository
                .findByDateBetween(LocalDate.of(anneeSource, 1, 1), LocalDate.of(anneeSource, 12, 31))
                .stream()
                .filter(f -> Boolean.TRUE.equals(f.getRecurrent()))
                .filter(f -> f.getDate() != null)
                .filter(f -> !(f.getDate().getMonthValue() == 2 && f.getDate().getDayOfMonth() == 29))
                .map(f -> JourFerie.builder()
                        .date(f.getDate().withYear(anneeCible))
                        .libelle(f.getLibelle())
                        .chome(f.getChome())
                        .recurrent(true)
                        .build())
                .filter(f -> !repository.existsByDate(f.getDate()))
                .map(this::horodater)
                .toList();

        return repository.saveAll(aCreer).stream()
                .sorted(Comparator.comparing(JourFerie::getDate))
                .map(this::toDto)
                .toList();
    }

    // --- Interne ------------------------------------------------------------

    private void exigerDroitEcriture() {
        if (!identite.estRh() && !identite.estSuperAdmin()) {
            throw new CongeAccesRefuseException(
                    "Seuls les profils RH et super-administrateur peuvent modifier le calendrier des jours fériés.");
        }
    }

    private JourFerie horodater(JourFerie entity) {
        entity.setDateModification(LocalDateTime.now());
        entity.setModifieParId(currentUserProvider.currentUserId());
        entity.setModifieParNom(currentUserProvider.currentUserNom());
        return entity;
    }

    private JourFerieDto toDto(JourFerie e) {
        return JourFerieDto.builder()
                .id(e.getId())
                .date(e.getDate())
                .libelle(e.getLibelle())
                .chome(e.getChome())
                .recurrent(e.getRecurrent())
                .dateModification(e.getDateModification())
                .modifieParId(e.getModifieParId())
                .modifieParNom(e.getModifieParNom())
                .build();
    }
}
