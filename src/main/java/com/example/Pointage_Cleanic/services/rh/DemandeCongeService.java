package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeCongeService {

    private final DemandeCongeRepository demandeCongeRepository;
    private final DossierEmployeRepository dossierEmployeRepository;
    private final CongeMapper mapper;
    private final CongeWorkflowService workflowService;

    /**
     * Jours de congé acquis par année pleine, en <b>jours ouvrés</b>.
     * Configurable — la valeur historique de 30 jours était erronée.
     */
    @Value("${app.conges.jours-acquis-par-an:22}")
    private int joursAcquisParAn;

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

    public List<DemandeCongeDto> getByEmployeId(String employeId) {
        return demandeCongeRepository.findByEmployeId(employeId).stream()
                .map(this::toDto).collect(Collectors.toList());
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
        DossierEmploye employe = dossierEmployeRepository.findById(employeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier employé introuvable : " + employeId));
        return buildSolde(employe);
    }

    /**
     * Soldes de tous les employés non sortis (façade /conges/soldes sans employeId).
     */
    public List<SoldeCongeDto> getSoldes() {
        return dossierEmployeRepository.findByStatutIn(List.of(
                        StatutDossierEmploye.ACTIF,
                        StatutDossierEmploye.EN_PERIODE_ESSAI,
                        StatutDossierEmploye.SUSPENDU))
                .stream()
                .map(this::buildSolde)
                .collect(Collectors.toList());
    }

    private SoldeCongeDto buildSolde(DossierEmploye employe) {
        int annee = LocalDate.now().getYear();
        LocalDate debut = LocalDate.of(annee, 1, 1);
        LocalDate fin = LocalDate.of(annee, 12, 31);

        List<DemandeConge> congesAnnee = demandeCongeRepository
                .findByEmployeIdAndDateDebutBetween(employe.getId(), debut, fin);

        int pris = congesAnnee.stream()
                .filter(c -> c.getStatut() == StatutDemande.APPROUVE)
                .mapToInt(c -> c.getNombreJours() != null ? c.getNombreJours() : 0)
                .sum();

        // Toute demande encore dans le circuit réserve des jours — pas seulement celles
        // au premier niveau, sinon une demande en cours de validation disparaîtrait du solde.
        int enCours = congesAnnee.stream()
                .filter(c -> c.getStatut() != null && c.getStatut().estEnCours())
                .mapToInt(c -> c.getNombreJours() != null ? c.getNombreJours() : 0)
                .sum();

        return SoldeCongeDto.builder()
                .employeId(employe.getId())
                .matricule(employe.getMatricule())
                .nom(employe.getNom())
                .prenom(employe.getPrenom())
                .departement(employe.getDepartement())
                .anneeReference(annee)
                .acquis(joursAcquisParAn)
                .pris(pris)
                .enCours(enCours)
                .solde(joursAcquisParAn - pris - enCours)
                .build();
    }

    /**
     * Liste filtrée + paginée des demandes pour la façade /api/temps-presences.
     * Filtrage en mémoire ; intervalle [dateDebut, dateFin] = chevauchement.
     */
    public Page<DemandeCongeDto> searchDemandes(
            String employeId, String departement, List<String> statuts, String type,
            LocalDate dateDebut, LocalDate dateFin, String q, String niveau, int page, int size) {

        List<DemandeCongeDto> filtered = demandeCongeRepository.findAll().stream()
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