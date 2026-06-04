package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDemande;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.DemandeCongeRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeCongeService {

    private static final int JOURS_ACQUIS_PAR_AN = 30;

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

        int annee = LocalDate.now().getYear();
        LocalDate debut = LocalDate.of(annee, 1, 1);
        LocalDate fin = LocalDate.of(annee, 12, 31);

        List<DemandeConge> congesAnnee = demandeCongeRepository
                .findByEmployeIdAndDateDebutBetween(employeId, debut, fin);

        int pris = congesAnnee.stream()
                .filter(c -> c.getStatut() == StatutDemande.APPROUVE)
                .mapToInt(c -> c.getNombreJours() != null ? c.getNombreJours() : 0)
                .sum();

        int enCours = congesAnnee.stream()
                .filter(c -> c.getStatut() == StatutDemande.EN_ATTENTE)
                .mapToInt(c -> c.getNombreJours() != null ? c.getNombreJours() : 0)
                .sum();

        return SoldeCongeDto.builder()
                .employeId(employeId)
                .matricule(employe.getMatricule())
                .nom(employe.getNom())
                .prenom(employe.getPrenom())
                .departement(employe.getDepartement())
                .anneeReference(annee)
                .acquis(JOURS_ACQUIS_PAR_AN)
                .pris(pris)
                .enCours(enCours)
                .solde(JOURS_ACQUIS_PAR_AN - pris - enCours)
                .build();
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