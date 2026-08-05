package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.CompteursAValiderDto;
import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.MonProfilCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.ActionValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.entities.rh.DecisionNiveau;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.HistoriqueValidationConge;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.exception.CongeInvalideException;
import com.example.Pointage_Cleanic.exception.CongeTransitionInterditeException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Circuit de validation des congés à 3 niveaux.
 *
 * <pre>
 *   EN_ATTENTE_SUPERIEUR → EN_ATTENTE_RH → EN_ATTENTE_DG → APPROUVE
 * </pre>
 *
 * <p>Un refus à n'importe quel niveau est terminal. Deux invariants gouvernent tout ce
 * service :
 * <ul>
 *   <li><b>Le niveau n'est jamais fourni par le client</b> : il est déduit du statut courant,
 *       et l'appelant doit être habilité à ce niveau précis (403 sinon, 409 si la demande a
 *       déjà changé de main) ;</li>
 *   <li><b>l'identité du décideur vient du JWT</b>, jamais du corps de requête.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CongeWorkflowService {

    /** Longueur minimale du motif de refus — il part par e-mail au demandeur. */
    public static final int MOTIF_REFUS_MIN_LENGTH = 10;

    private final DemandeCongeRepository demandeCongeRepository;
    private final DossierEmployeRepository dossierEmployeRepository;
    private final CongeIdentiteService identite;
    private final CongeMapper mapper;
    private final CongeNotificationService notificationService;
    private final CongeMailNotificationService mailService;

    // ─── Profil de l'appelant ─────────────────────────────────────────────────

    public MonProfilCongeDto monProfil() {
        Optional<DossierEmploye> employe = identite.employeCourant();
        List<NiveauValidationConge> niveaux = identite.niveauxValidables();

        return MonProfilCongeDto.builder()
                .employeId(employe.map(DossierEmploye::getId).orElse(null))
                .matricule(employe.map(DossierEmploye::getMatricule).orElse(null))
                .nom(employe.map(DossierEmploye::getNom).orElse(null))
                .prenom(employe.map(DossierEmploye::getPrenom).orElse(null))
                .email(identite.emailCourant())
                .departement(employe.map(DossierEmploye::getDepartement).orElse(null))
                .superieurHierarchiqueId(employe.map(DossierEmploye::getSuperieurHierarchiqueId).orElse(null))
                .superieurHierarchiqueNom(employe.map(DossierEmploye::getSuperieurHierarchiqueNom).orElse(null))
                .niveauxValidables(niveaux)
                .nbDemandesAValider(compteurs().getTotal())
                .build();
    }

    // ─── Création ─────────────────────────────────────────────────────────────

    /**
     * Dépose une demande. Le demandeur est l'employé du JWT, sauf si l'appelant est
     * {@code RH}/{@code SUPERADMIN} et fournit explicitement un {@code employeId}.
     */
    public DemandeCongeDto creer(DemandeCongeDto dto) {
        DossierEmploye demandeur = resoudreDemandeur(dto);

        if (dto.getDateDebut() == null || dto.getDateFin() == null) {
            throw new CongeInvalideException("Les dates de début et de fin sont obligatoires.");
        }
        if (dto.getDateFin().isBefore(dto.getDateDebut())) {
            throw new CongeInvalideException("La date de fin doit être postérieure à la date de début.");
        }

        DemandeConge demande = mapper.toEntity(dto);
        demande.setEmployeId(demandeur.getId());
        demande.setMatricule(demandeur.getMatricule());
        demande.setNom(demandeur.getNom());
        demande.setPrenom(demandeur.getPrenom());
        demande.setDepartement(demandeur.getDepartement());
        demande.setNombreJours(CongeCalendrier.joursOuvres(dto.getDateDebut(), dto.getDateFin()));
        demande.setDateDemande(LocalDate.now());

        // Validateur N1 figé à la création : un changement d'organigramme ne doit pas
        // rerouter une demande déjà en vol.
        demande.setSuperieurHierarchiqueId(demandeur.getSuperieurHierarchiqueId());
        demande.setSuperieurHierarchiqueNom(demandeur.getSuperieurHierarchiqueNom());

        boolean sansSuperieur = demandeur.getSuperieurHierarchiqueId() == null
                || demandeur.getSuperieurHierarchiqueId().isBlank();
        demande.setNiveauSuperieurIgnore(sansSuperieur);
        demande.setStatut(sansSuperieur
                ? StatutDemande.EN_ATTENTE_RH
                : StatutDemande.EN_ATTENTE_SUPERIEUR);

        demande.tracer(entree(ActionValidationConge.CREATION, null, null));
        if (sansSuperieur) {
            // Le circuit démarre à la RH — on trace pourquoi, mais on ne bloque jamais.
            demande.tracer(entree(ActionValidationConge.VALIDATION, NiveauValidationConge.SUPERIEUR,
                    "Niveau ignoré — aucun supérieur hiérarchique renseigné"));
        }

        DemandeConge sauvee = demandeCongeRepository.save(demande);
        notifier(sauvee, "DEMANDE_SOUMISE", "Nouvelle demande de congé",
                "%s a déposé une demande de congé.".formatted(nomComplet(sauvee)));
        mailService.notifierCreation(sauvee);

        return decorer(mapper.toDto(sauvee));
    }

    private DossierEmploye resoudreDemandeur(DemandeCongeDto dto) {
        String employeIdDemande = dto.getEmployeId();
        Optional<DossierEmploye> moi = identite.employeCourant();

        boolean pourAutrui = employeIdDemande != null && !employeIdDemande.isBlank()
                && moi.map(m -> !m.getId().equals(employeIdDemande)).orElse(true);

        if (pourAutrui) {
            if (!identite.peutCreerPourAutrui()) {
                throw new CongeAccesRefuseException(
                        "Seuls les profils RH peuvent déposer une demande pour un autre employé.");
            }
            return dossierEmployeRepository.findById(employeIdDemande)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Dossier employé introuvable : " + employeIdDemande));
        }

        return moi.orElseThrow(() -> new CongeInvalideException(
                "Votre compte n'est rattaché à aucun dossier employé : "
                        + "contactez les Ressources humaines."));
    }

    // ─── Transitions ──────────────────────────────────────────────────────────

    /** Fait avancer la demande d'un cran. Le niveau est déduit du statut courant. */
    public DemandeCongeDto valider(String id, String commentaire) {
        DemandeConge demande = charger(id);
        NiveauValidationConge niveau = niveauCourantOuConflit(demande);
        exigerHabilitation(demande, niveau);

        DecisionNiveau decision = DecisionNiveau.builder()
                .decideurId(identite.idUtilisateurCourant())
                .decideurNom(identite.nomCourant())
                .date(LocalDateTime.now())
                .commentaire(vide(commentaire) ? null : commentaire.trim())
                .build();
        appliquerDecision(demande, niveau, decision);

        StatutDemande suivant = niveau.statutApresValidation();
        demande.setStatut(suivant);
        demande.tracer(entree(ActionValidationConge.VALIDATION, niveau, decision.getCommentaire()));

        if (suivant == StatutDemande.APPROUVE) {
            // Décision finale dupliquée dans les champs plats historiques.
            demande.setDateDecision(LocalDate.now());
            demande.setDecideurId(decision.getDecideurId());
            demande.setDecideurNom(decision.getDecideurNom());
            demande.setCommentaireDecision(decision.getCommentaire());
        }

        DemandeConge sauvee = demandeCongeRepository.save(demande);

        if (suivant == StatutDemande.APPROUVE) {
            notifier(sauvee, "DEMANDE_APPROUVEE", "Demande de congé approuvée",
                    "La demande de %s est approuvée.".formatted(nomComplet(sauvee)));
        } else {
            notifier(sauvee, "DEMANDE_VALIDEE_NIVEAU", "Demande de congé validée",
                    "La demande de %s passe au niveau suivant.".formatted(nomComplet(sauvee)));
        }
        mailService.notifierValidation(sauvee, niveau);

        return decorer(mapper.toDto(sauvee));
    }

    /** Refus terminal — le motif est obligatoire, il est transmis au demandeur par e-mail. */
    public DemandeCongeDto refuser(String id, String motif) {
        if (vide(motif) || motif.trim().length() < MOTIF_REFUS_MIN_LENGTH) {
            throw new CongeInvalideException(
                    "Le motif du refus est obligatoire (%d caractères minimum)."
                            .formatted(MOTIF_REFUS_MIN_LENGTH));
        }

        DemandeConge demande = charger(id);
        NiveauValidationConge niveau = niveauCourantOuConflit(demande);
        exigerHabilitation(demande, niveau);

        String motifNet = motif.trim();
        DecisionNiveau decision = DecisionNiveau.builder()
                .decideurId(identite.idUtilisateurCourant())
                .decideurNom(identite.nomCourant())
                .date(LocalDateTime.now())
                .commentaire(motifNet)
                .build();
        appliquerDecision(demande, niveau, decision);

        demande.setStatut(StatutDemande.REFUSE);
        demande.setNiveauRefus(niveau);
        demande.setMotifRefus(motifNet);
        demande.setDateDecision(LocalDate.now());
        demande.setDecideurId(decision.getDecideurId());
        demande.setDecideurNom(decision.getDecideurNom());
        demande.setCommentaireDecision(motifNet);
        demande.tracer(entree(ActionValidationConge.REFUS, niveau, motifNet));

        DemandeConge sauvee = demandeCongeRepository.save(demande);
        notifier(sauvee, "DEMANDE_REFUSEE", "Demande de congé refusée",
                "La demande de %s a été refusée.".formatted(nomComplet(sauvee)));
        mailService.notifierRefus(sauvee, niveau, motifNet);

        return decorer(mapper.toDto(sauvee));
    }

    /** Annulation par le demandeur, la RH ou le super-admin, tant que le circuit est ouvert. */
    public void annuler(String id) {
        DemandeConge demande = charger(id);

        if (!demande.getStatut().estEnCours()) {
            throw new CongeTransitionInterditeException(
                    "Cette demande n'est plus en cours : elle ne peut plus être annulée.");
        }
        boolean estMienne = identite.employeIdCourant() != null
                && identite.employeIdCourant().equals(demande.getEmployeId());
        if (!estMienne && !identite.peutCreerPourAutrui()) {
            throw new CongeAccesRefuseException(
                    "Vous ne pouvez annuler que vos propres demandes de congé.");
        }

        NiveauValidationConge niveauAvant = NiveauValidationConge
                .depuisStatut(demande.getStatut()).orElse(null);

        demande.setStatut(StatutDemande.ANNULE);
        demande.tracer(entree(ActionValidationConge.ANNULATION, null, null));

        DemandeConge sauvee = demandeCongeRepository.save(demande);
        notifier(sauvee, "DEMANDE_ANNULEE", "Demande de congé annulée",
                "La demande de %s a été annulée.".formatted(nomComplet(sauvee)));
        mailService.notifierAnnulation(sauvee, niveauAvant);
    }

    // ─── Files de lecture ─────────────────────────────────────────────────────

    /** Demandes de l'appelant (demandeur déduit du JWT). */
    public Page<DemandeCongeDto> mesDemandes(int page, int size) {
        String employeId = identite.employeIdCourant();
        Pageable pageable = PageRequest.of(page, size);
        if (employeId == null) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        return demandeCongeRepository
                .findByEmployeIdOrderByDateDemandeDesc(employeId, pageable)
                .map(e -> decorer(mapper.toDto(e)));
    }

    /**
     * File de validation : uniquement ce que l'appelant peut trancher <b>maintenant</b>.
     * Chaque ligne porte {@code peutValiderParMoi = true}.
     */
    public Page<DemandeCongeDto> demandesAValider(NiveauValidationConge niveau, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<NiveauValidationConge> niveaux = niveauxCibles(niveau);
        if (niveaux.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Niveau 1 : demandes des subordonnés directs (sauf super-admin, qui voit tout).
        if (niveaux.equals(List.of(NiveauValidationConge.SUPERIEUR)) && !identite.estSuperAdmin()) {
            String moi = identite.employeIdCourant();
            if (moi == null) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            return demandeCongeRepository
                    .findBySuperieurHierarchiqueIdAndStatutInOrderByDateDemandeDesc(
                            moi, statutsDe(niveaux), pageable)
                    .map(e -> decorer(mapper.toDto(e)));
        }

        // Niveaux 2 et 3 (et super-admin) : filtrage par statut.
        Page<DemandeCongeDto> resultat = demandeCongeRepository
                .findByStatutInOrderByDateDemandeDesc(statutsDe(niveaux), pageable)
                .map(e -> decorer(mapper.toDto(e)));

        // Un supérieur non super-admin ne doit voir que ses subordonnés dans la file « toutes ».
        if (!identite.estSuperAdmin() && niveaux.contains(NiveauValidationConge.SUPERIEUR)) {
            String moi = identite.employeIdCourant();
            List<DemandeCongeDto> filtre = resultat.getContent().stream()
                    .filter(d -> d.getNiveauCourant() != NiveauValidationConge.SUPERIEUR
                            || (moi != null && moi.equals(d.getSuperieurHierarchiqueId())))
                    .toList();
            return new PageImpl<>(filtre, pageable, resultat.getTotalElements());
        }
        return resultat;
    }

    public CompteursAValiderDto compteurs() {
        Map<NiveauValidationConge, Long> parNiveau = new EnumMap<>(NiveauValidationConge.class);
        for (NiveauValidationConge n : NiveauValidationConge.values()) {
            parNiveau.put(n, 0L);
        }

        List<NiveauValidationConge> validables = identite.niveauxValidables();
        boolean superAdmin = identite.estSuperAdmin();
        String moi = identite.employeIdCourant();

        for (NiveauValidationConge n : validables) {
            long compte;
            if (n == NiveauValidationConge.SUPERIEUR && !superAdmin) {
                compte = moi == null ? 0L
                        : demandeCongeRepository.countBySuperieurHierarchiqueIdAndStatutIn(
                                moi, statutsDe(List.of(n)));
            } else {
                compte = demandeCongeRepository.countByStatutIn(statutsDe(List.of(n)));
            }
            parNiveau.put(n, compte);
        }

        long total = parNiveau.values().stream().mapToLong(Long::longValue).sum();
        return CompteursAValiderDto.builder().total(total).parNiveau(parNiveau).build();
    }

    /** Fiche d'une demande, décorée pour l'appelant (historique + peutValiderParMoi). */
    public DemandeCongeDto getPourAppelant(String id) {
        return decorer(mapper.toDto(charger(id)));
    }

    /** Renseigne {@code peutValiderParMoi} — autorité unique du front pour ses boutons. */
    public DemandeCongeDto decorer(DemandeCongeDto dto) {
        if (dto == null) {
            return null;
        }
        dto.setPeutValiderParMoi(peutValider(dto.getStatut(), dto.getSuperieurHierarchiqueId()));
        return dto;
    }

    // ─── Habilitations ────────────────────────────────────────────────────────

    private boolean peutValider(StatutDemande statut, String superieurHierarchiqueId) {
        Optional<NiveauValidationConge> niveau = NiveauValidationConge.depuisStatut(statut);
        if (niveau.isEmpty()) {
            return false;
        }
        if (identite.estSuperAdmin()) {
            return true;
        }
        return switch (niveau.get()) {
            case SUPERIEUR -> {
                String moi = identite.employeIdCourant();
                yield moi != null && moi.equals(superieurHierarchiqueId);
            }
            case RH -> identite.estRh();
            case DIRECTION_GENERALE -> false;   // réservé au super-admin, traité plus haut
        };
    }

    private void exigerHabilitation(DemandeConge demande, NiveauValidationConge niveau) {
        if (!peutValider(demande.getStatut(), demande.getSuperieurHierarchiqueId())) {
            throw new CongeAccesRefuseException(
                    "Vous n'êtes pas habilité à trancher cette demande au niveau %s."
                            .formatted(libelle(niveau)));
        }
    }

    private NiveauValidationConge niveauCourantOuConflit(DemandeConge demande) {
        return NiveauValidationConge.depuisStatut(demande.getStatut())
                .orElseThrow(() -> new CongeTransitionInterditeException(
                        "Cette demande est déjà clôturée (%s) : aucune décision n'est possible."
                                .formatted(demande.getStatut())));
    }

    // ─── Utilitaires ──────────────────────────────────────────────────────────

    private DemandeConge charger(String id) {
        return demandeCongeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de congé introuvable : " + id));
    }

    private void appliquerDecision(DemandeConge demande, NiveauValidationConge niveau,
                                   DecisionNiveau decision) {
        switch (niveau) {
            case SUPERIEUR -> demande.setDecisionSuperieur(decision);
            case RH -> demande.setDecisionRh(decision);
            case DIRECTION_GENERALE -> demande.setDecisionDg(decision);
        }
    }

    private HistoriqueValidationConge entree(ActionValidationConge action,
                                             NiveauValidationConge niveau, String commentaire) {
        return HistoriqueValidationConge.builder()
                .action(action)
                .niveau(niveau)
                .auteurId(identite.idUtilisateurCourant())
                .auteurNom(identite.nomCourant())
                .date(LocalDateTime.now())
                .commentaire(commentaire)
                .build();
    }

    /** Niveaux visés par une requête de file : celui demandé, sinon tous ceux de l'appelant. */
    private List<NiveauValidationConge> niveauxCibles(NiveauValidationConge demande) {
        List<NiveauValidationConge> validables = identite.niveauxValidables();
        if (demande == null) {
            return validables;
        }
        return validables.contains(demande) ? List.of(demande) : List.of();
    }

    private List<StatutDemande> statutsDe(List<NiveauValidationConge> niveaux) {
        return niveaux.stream()
                .flatMap(n -> n == NiveauValidationConge.SUPERIEUR
                        // Le statut legacy EN_ATTENTE est assimilé au niveau 1.
                        ? java.util.stream.Stream.of(StatutDemande.EN_ATTENTE_SUPERIEUR, StatutDemande.EN_ATTENTE)
                        : java.util.stream.Stream.of(n.statutAttente()))
                .toList();
    }

    private void notifier(DemandeConge demande, String type, String titre, String message) {
        notificationService.diffuserEtCiblerValidateurs(demande, type, titre, message);
    }

    private static String nomComplet(DemandeConge d) {
        String complet = "%s %s".formatted(
                d.getPrenom() == null ? "" : d.getPrenom(),
                d.getNom() == null ? "" : d.getNom()).trim();
        return complet.isBlank() ? d.getEmployeId() : complet;
    }

    private static String libelle(NiveauValidationConge niveau) {
        return switch (niveau) {
            case SUPERIEUR -> "supérieur hiérarchique";
            case RH -> "Ressources humaines";
            case DIRECTION_GENERALE -> "Direction générale";
        };
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
