package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.EmployeSelectionnableDto;
import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.Enum.rh.TypeConge;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.CongeAccesRefuseException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class DemandeCongeService {

    private final DemandeCongeRepository demandeCongeRepository;
    private final DossierEmployeRepository dossierEmployeRepository;
    private final CongeMapper mapper;
    private final CongeWorkflowService workflowService;
    private final CongeIdentiteService identite;
    private final CongeAcquisCalculator acquisCalculator;

    /**
     * Dépôt d'une demande : délégué au circuit de validation, qui résout le demandeur
     * depuis le JWT, fige le validateur de niveau 1 et pose le statut initial.
     */
    public DemandeCongeDto create(DemandeCongeDto dto) {
        return workflowService.creer(dto);
    }

    public DemandeCongeDto getById(String id) {
        return toDto(demandeCongeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de congé introuvable : " + id)));
    }

    public List<DemandeCongeDto> getAll() {
        return demandeCongeRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Historique complet des demandes d'un employé, <b>de la plus récente à la plus
     * ancienne</b> (dates de demande absentes en dernier) — c'est l'ordre attendu par
     * l'onglet Congés de la fiche employé. Même tri que {@link #searchDemandes}.
     */
    public List<DemandeCongeDto> getByEmployeId(String employeId) {
        exigerVisibilite(employeId);
        return demandeCongeRepository.findByEmployeId(employeId).stream()
                .map(this::toDto)
                .sorted(Comparator.comparing(DemandeCongeDto::getDateDemande,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    public DemandeCongeDto update(String id, DemandeCongeDto dto) {
        DemandeConge existing = demandeCongeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de congé introuvable : " + id));
        existing.setType(dto.getType());
        existing.setDateDebut(dto.getDateDebut());
        existing.setDateFin(dto.getDateFin());
        existing.setNombreJours(computeNombreJours(dto.getDateDebut(), dto.getDateFin()));
        existing.setMotif(dto.getMotif());
        return toDto(demandeCongeRepository.save(existing));
    }

    /** Annulation : le circuit vérifie que l'appelant est le demandeur (ou RH/super-admin). */
    public void delete(String id) {
        workflowService.annuler(id);
    }

    /** Valide le niveau courant du circuit — le niveau est déduit serveur du statut. */
    public DemandeCongeDto valider(String id, String commentaire) {
        return workflowService.valider(id, commentaire);
    }

    /** @deprecated remplacé par {@link #valider(String, String)}. */
    @Deprecated
    public DemandeCongeDto approuver(String id, String commentaire) {
        return workflowService.valider(id, commentaire);
    }

    public DemandeCongeDto refuser(String id, String motif) {
        return workflowService.refuser(id, motif);
    }

    public SoldeCongeDto getSolde(String employeId) {
        exigerVisibilite(employeId);
        DossierEmploye employe = dossierEmployeRepository.findById(employeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier employé introuvable : " + employeId));
        return buildSolde(employe);
    }

    /**
     * Soldes des employés non sortis <b>visibles par l'appelant</b> (façade /conges/soldes
     * sans employeId) : tout le monde pour la RH et le super-admin, soi-même et ses
     * subordonnés directs sinon.
     */
    public List<SoldeCongeDto> getSoldes() {
        List<StatutDossierEmploye> statutsActifs = List.of(
                StatutDossierEmploye.ACTIF,
                StatutDossierEmploye.EN_PERIODE_ESSAI,
                StatutDossierEmploye.SUSPENDU);

        PerimetreConges perimetre = identite.perimetreLecture();
        List<DossierEmploye> employes;
        if (perimetre.voitTout()) {
            employes = dossierEmployeRepository.findByStatutIn(statutsActifs);
        } else if (perimetre.estVide()) {
            employes = List.of();
        } else {
            // On part du périmètre plutôt que de scanner tous les dossiers : quelques ids.
            employes = StreamSupport
                    .stream(dossierEmployeRepository.findAllById(perimetre.employesVisibles()).spliterator(), false)
                    .filter(e -> statutsActifs.contains(e.getStatut()))
                    .collect(Collectors.toList());
        }

        return employes.stream().map(this::buildSolde).collect(Collectors.toList());
    }

    /**
     * Employés au nom desquels l'appelant peut déposer une demande — alimente le champ
     * « Employé » du formulaire de demande.
     *
     * <p>S'appuie sur {@code perimetreDepot()} et <b>non</b> sur {@code perimetreLecture()} :
     * tout encadrant <i>voit</i> les congés de ses subordonnés, mais seul le rôle
     * {@code EXPLOITATION} peut en <i>déposer</i> pour eux. Un compte non rattaché à un
     * dossier employé reçoit une liste <b>vide</b> — jamais totale — et le formulaire en
     * déduit que le dépôt est impossible.
     *
     * <p>Tri : soi d'abord (le cas de loin le plus fréquent), puis nom et prénom croissants.
     */
    public List<EmployeSelectionnableDto> getEmployesSelectionnables() {
        List<StatutDossierEmploye> statutsActifs = List.of(
                StatutDossierEmploye.ACTIF,
                StatutDossierEmploye.EN_PERIODE_ESSAI,
                StatutDossierEmploye.SUSPENDU);

        PerimetreConges perimetre = identite.perimetreDepot();
        List<DossierEmploye> employes;
        if (perimetre.voitTout()) {
            employes = dossierEmployeRepository.findByStatutIn(statutsActifs);
        } else if (perimetre.estVide()) {
            employes = List.of();
        } else {
            // On part du périmètre plutôt que de scanner tous les dossiers : quelques ids.
            employes = StreamSupport
                    .stream(dossierEmployeRepository.findAllById(perimetre.employesVisibles()).spliterator(), false)
                    .filter(e -> statutsActifs.contains(e.getStatut()))
                    .collect(Collectors.toList());
        }

        String moi = perimetre.moi();
        return employes.stream()
                .map(e -> EmployeSelectionnableDto.builder()
                        .id(e.getId())
                        .matricule(e.getMatricule())
                        .nom(e.getNom())
                        .prenom(e.getPrenom())
                        .departement(e.getDepartement())
                        .superieurHierarchiqueId(e.getSuperieurHierarchiqueId())
                        .superieurHierarchiqueNom(e.getSuperieurHierarchiqueNom())
                        .estMoi(e.getId() != null && e.getId().equals(moi))
                        .build())
                .sorted(Comparator.comparing(EmployeSelectionnableDto::isEstMoi).reversed()
                        .thenComparing(d -> d.getNom() == null ? "" : d.getNom(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(d -> d.getPrenom() == null ? "" : d.getPrenom(),
                                String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /**
     * Garde de lecture par employé, à poser <b>avant</b> tout {@code findById} : refuser
     * après aurait divulgué l'existence (ou non) du dossier via un 404 distinct du 403.
     */
    private void exigerVisibilite(String employeId) {
        if (!identite.perimetreLecture().voitEmploye(employeId)) {
            throw new CongeAccesRefuseException(
                    "Vous n'êtes pas autorisé à consulter les congés de cet employé.");
        }
    }

    /**
     * Seul le congé annuel ampute les jours acquis — cf. {@link TypeConge#decompteSoldeAnnuel()}.
     * Un repos médical, un congé maternité ou un congé sans solde n'entament pas le compteur
     * de congés payés.
     *
     * <p>Un {@code type} nul — données antérieures, le DTO n'a jamais porté de {@code @NotNull}
     * sur ce champ — est <b>compté</b> : sous-estimer un solde est moins grave que d'en
     * créditer à tort.
     */
    private static boolean decompteLeSolde(DemandeConge conge) {
        return conge.getType() == null || conge.getType().decompteSoldeAnnuel();
    }

    /**
     * Solde de l'exercice courant, augmenté du <b>reliquat des exercices antérieurs</b>.
     *
     * <p>L'acquis n'est plus une constante : il vaut 2 jours ouvrables par mois de service
     * effectif ({@link CongeAcquisCalculator}), calculés depuis la date d'entrée de l'employé.
     *
     * <p>Une <b>seule</b> lecture ramène tout l'historique de l'employé : le reliquat impose de
     * parcourir les exercices clos, et refaire une requête par année serait N requêtes pour un
     * volume qui tient largement en mémoire (quelques dizaines de demandes par carrière).
     */
    private SoldeCongeDto buildSolde(DossierEmploye employe) {
        LocalDate aujourdhui = LocalDate.now();
        int annee = aujourdhui.getYear();
        LocalDate dateEntree = employe.getDateEntree();

        List<DemandeConge> decomptees = demandeCongeRepository.findByEmployeId(employe.getId())
                .stream()
                .filter(DemandeCongeService::decompteLeSolde)
                .filter(c -> c.getDateDebut() != null)
                .collect(Collectors.toList());

        int pris = joursParStatut(decomptees, annee, false);

        // Toute demande encore dans le circuit réserve des jours — pas seulement celles
        // au premier niveau, sinon une demande en cours de validation disparaîtrait du solde.
        int enCours = joursParStatut(decomptees, annee, true);

        int acquis = acquisCalculator.acquis(annee, dateEntree, aujourdhui);
        int soldeAnterieur = soldeAnterieur(decomptees, annee, dateEntree, aujourdhui);

        return SoldeCongeDto.builder()
                .employeId(employe.getId())
                .matricule(employe.getMatricule())
                .nom(employe.getNom())
                .prenom(employe.getPrenom())
                .departement(employe.getDepartement())
                .anneeReference(annee)
                .soldeAnterieur(soldeAnterieur)
                .moisAcquis(acquisCalculator.moisAcquis(annee, dateEntree, aujourdhui))
                .acquis(acquis)
                .pris(pris)
                .enCours(enCours)
                // Le report est consommable ; jamais de solde négatif à l'affichage.
                .solde(Math.max(0, soldeAnterieur + acquis - pris - enCours))
                .build();
    }

    /**
     * Reliquat cumulé des exercices <b>clos</b>, de l'année d'entrée à N-1.
     *
     * <p>Le cumul est planché à 0 une seule fois, <b>sur le total</b> et non année par année :
     * un dépassement de droits en 2024 doit s'imputer sur le reliquat 2025, sinon le report
     * serait systématiquement surévalué.
     *
     * <p>Seules les demandes <b>approuvées</b> amputent un exercice clos : une demande restée en
     * attente depuis 2024 ne sera jamais tranchée, elle ne doit pas geler du reliquat.
     *
     * <p>Sans date d'entrée, il n'existe aucune base pour reconstituer un historique : le report
     * est nul plutôt qu'inventé — même arbitrage prudent que le {@code type} nul de
     * {@link #decompteLeSolde}, où l'on préfère sous-estimer un solde qu'en créditer à tort.
     */
    private int soldeAnterieur(List<DemandeConge> decomptees, int anneeCourante,
                               LocalDate dateEntree, LocalDate aujourdhui) {
        if (dateEntree == null) {
            return 0;
        }
        int cumul = 0;
        for (int a = dateEntree.getYear(); a < anneeCourante; a++) {
            cumul += acquisCalculator.acquis(a, dateEntree, aujourdhui)
                    - joursParStatut(decomptees, a, false);
        }
        return Math.max(0, cumul);
    }

    /**
     * Jours d'un exercice, rattaché par la seule {@code dateDebut} : un congé à cheval sur le
     * 31/12 est intégralement imputé à son année de début (règle historique, inchangée — la
     * modifier ferait bouger des soldes déjà validés).
     *
     * @param enCours {@code true} pour les demandes encore dans le circuit, {@code false} pour
     *                les seules demandes approuvées
     */
    private static int joursParStatut(List<DemandeConge> decomptees, int annee, boolean enCours) {
        return decomptees.stream()
                .filter(c -> c.getDateDebut().getYear() == annee)
                .filter(c -> enCours
                        ? c.getStatut() != null && c.getStatut().estEnCours()
                        : c.getStatut() == StatutDemande.APPROUVE)
                .mapToInt(c -> c.getNombreJours() != null ? c.getNombreJours() : 0)
                .sum();
    }

    /**
     * Liste filtrée + paginée des demandes pour la façade /api/temps-presences.
     * Filtrage en mémoire ; intervalle [dateDebut, dateFin] = chevauchement.
     *
     * <p>Le <b>périmètre de lecture</b> s'applique en tête de chaîne, sur l'entité : il
     * économise le mapping DTO et, surtout, il rend {@code totalElements} cohérent avec ce
     * que l'appelant a le droit de voir (filtrer après la pagination donnerait des pages
     * partiellement vides et un compteur faux).
     */
    public Page<DemandeCongeDto> searchDemandes(
            String employeId, String departement, List<String> statuts, String type,
            LocalDate dateDebut, LocalDate dateFin, String q, String niveau, int page, int size) {

        PerimetreConges perimetre = identite.perimetreLecture();
        if (perimetre.estVide()) {
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }

        List<DemandeCongeDto> filtered = demandeCongeRepository.findAll().stream()
                .filter(e -> perimetre.voitDemande(e.getEmployeId(), e.getSuperieurHierarchiqueId()))
                .map(this::toDto)
                .filter(d -> employeId == null || employeId.isBlank() || employeId.equals(d.getEmployeId()))
                .filter(d -> departement == null || departement.isBlank()
                        || (d.getDepartement() != null && d.getDepartement().equalsIgnoreCase(departement)))
                .filter(d -> statuts == null || statuts.isEmpty()
                        || (d.getStatut() != null
                            && statuts.stream().anyMatch(s -> d.getStatut().name().equalsIgnoreCase(s))))
                .filter(d -> niveau == null || niveau.isBlank()
                        || (d.getNiveauCourant() != null
                            && d.getNiveauCourant().name().equalsIgnoreCase(niveau)))
                .filter(d -> type == null || type.isBlank()
                        || (d.getType() != null && d.getType().name().equalsIgnoreCase(type)))
                .filter(d -> dateDebut == null || (d.getDateFin() != null && !d.getDateFin().isBefore(dateDebut)))
                .filter(d -> dateFin == null || (d.getDateDebut() != null && !d.getDateDebut().isAfter(dateFin)))
                .filter(d -> matchesQ(q, d.getNom(), d.getPrenom(), d.getMatricule()))
                .sorted(Comparator.comparing(DemandeCongeDto::getDateDemande,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<DemandeCongeDto> content = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        content.forEach(workflowService::decorer);
        return new PageImpl<>(content, pageable, filtered.size());
    }

    private boolean matchesQ(String q, String nom, String prenom, String matricule) {
        if (q == null || q.isBlank()) return true;
        String s = q.toLowerCase();
        return (nom != null && nom.toLowerCase().contains(s))
                || (prenom != null && prenom.toLowerCase().contains(s))
                || (matricule != null && matricule.toLowerCase().contains(s));
    }

    /** Jours ouvrés (week-ends exclus) — même unité que le solde acquis. */
    private int computeNombreJours(LocalDate debut, LocalDate fin) {
        return CongeCalendrier.joursOuvres(debut, fin);
    }

    private DemandeCongeDto toDto(DemandeConge e) {
        return mapper.toDto(e);
    }
}