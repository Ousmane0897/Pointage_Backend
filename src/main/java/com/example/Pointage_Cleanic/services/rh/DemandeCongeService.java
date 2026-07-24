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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeCongeService {

    private static final int JOURS_ACQUIS_PAR_AN = 22;

    private final DemandeCongeRepository demandeCongeRepository;
    private final DossierEmployeRepository dossierEmployeRepository;

    public DemandeCongeDto create(DemandeCongeDto dto) {
        DossierEmploye employe = dossierEmployeRepository.findById(dto.getEmployeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier employé introuvable : " + dto.getEmployeId()));

        DemandeConge demande = toEntity(dto);
        // Snapshot employé
        demande.setMatricule(employe.getMatricule());
        demande.setNom(employe.getNom());
        demande.setPrenom(employe.getPrenom());
        demande.setDepartement(employe.getDepartement());
        demande.setNombreJours(computeNombreJours(demande.getDateDebut(), demande.getDateFin()));
        demande.setDateDemande(LocalDate.now());
        if (demande.getStatut() == null) demande.setStatut(StatutDemande.EN_ATTENTE);

        return toDto(demandeCongeRepository.save(demande));
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

    public void delete(String id) {
        DemandeConge demande = demandeCongeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de congé introuvable : " + id));
        demandeCongeRepository.delete(demande);
    }

    public DemandeCongeDto approuver(String id, String decideurId, String decideurNom, String commentaire) {
        DemandeConge demande = demandeCongeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de congé introuvable : " + id));
        demande.setStatut(StatutDemande.APPROUVE);
        demande.setDateDecision(LocalDate.now());
        demande.setDecideurId(decideurId);
        demande.setDecideurNom(decideurNom);
        demande.setCommentaireDecision(commentaire);
        return toDto(demandeCongeRepository.save(demande));
    }

    public DemandeCongeDto refuser(String id, String decideurId, String decideurNom, String commentaire) {
        DemandeConge demande = demandeCongeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de congé introuvable : " + id));
        demande.setStatut(StatutDemande.REFUSE);
        demande.setDateDecision(LocalDate.now());
        demande.setDecideurId(decideurId);
        demande.setDecideurNom(decideurNom);
        demande.setCommentaireDecision(commentaire);
        return toDto(demandeCongeRepository.save(demande));
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

        int enCours = congesAnnee.stream()
                .filter(c -> c.getStatut() == StatutDemande.EN_ATTENTE)
                .mapToInt(c -> c.getNombreJours() != null ? c.getNombreJours() : 0)
                .sum();

        return SoldeCongeDto.builder()
                .employeId(employe.getId())
                .matricule(employe.getMatricule())
                .nom(employe.getNom())
                .prenom(employe.getPrenom())
                .departement(employe.getDepartement())
                .anneeReference(annee)
                .acquis(JOURS_ACQUIS_PAR_AN)
                .pris(pris)
                .enCours(enCours)
                .solde(Math.max(0, JOURS_ACQUIS_PAR_AN - pris - enCours))
                .build();
    }

    /**
     * Liste filtrée + paginée des demandes pour la façade /api/temps-presences.
     * Filtrage en mémoire ; intervalle [dateDebut, dateFin] = chevauchement.
     */
    public Page<DemandeCongeDto> searchDemandes(
            String employeId, String departement, String statut, String type,
            LocalDate dateDebut, LocalDate dateFin, String q, int page, int size) {

        List<DemandeCongeDto> filtered = demandeCongeRepository.findAll().stream()
                .map(this::toDto)
                .filter(d -> employeId == null || employeId.isBlank() || employeId.equals(d.getEmployeId()))
                .filter(d -> departement == null || departement.isBlank()
                        || (d.getDepartement() != null && d.getDepartement().equalsIgnoreCase(departement)))
                .filter(d -> statut == null || statut.isBlank()
                        || (d.getStatut() != null && d.getStatut().name().equalsIgnoreCase(statut)))
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
        return new PageImpl<>(content, pageable, filtered.size());
    }

    private boolean matchesQ(String q, String nom, String prenom, String matricule) {
        if (q == null || q.isBlank()) return true;
        String s = q.toLowerCase();
        return (nom != null && nom.toLowerCase().contains(s))
                || (prenom != null && prenom.toLowerCase().contains(s))
                || (matricule != null && matricule.toLowerCase().contains(s));
    }

    private int computeNombreJours(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) return 0;
        return (int) ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    private DemandeCongeDto toDto(DemandeConge e) {
        return DemandeCongeDto.builder()
                .id(e.getId()).employeId(e.getEmployeId()).matricule(e.getMatricule())
                .nom(e.getNom()).prenom(e.getPrenom()).departement(e.getDepartement())
                .type(e.getType()).dateDebut(e.getDateDebut()).dateFin(e.getDateFin())
                .nombreJours(e.getNombreJours()).motif(e.getMotif()).statut(e.getStatut())
                .dateDemande(e.getDateDemande()).dateDecision(e.getDateDecision())
                .decideurId(e.getDecideurId()).decideurNom(e.getDecideurNom())
                .commentaireDecision(e.getCommentaireDecision())
                .build();
    }

    private DemandeConge toEntity(DemandeCongeDto dto) {
        return DemandeConge.builder()
                .employeId(dto.getEmployeId()).matricule(dto.getMatricule())
                .nom(dto.getNom()).prenom(dto.getPrenom()).departement(dto.getDepartement())
                .type(dto.getType()).dateDebut(dto.getDateDebut()).dateFin(dto.getDateFin())
                .motif(dto.getMotif()).statut(dto.getStatut())
                .build();
    }
}