package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.HeureSupplementaireDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutValidationHS;
import com.example.Pointage_Cleanic.Enum.rh.TypeMajoration;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.HeureSupplementaire;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.rh.HeureSupplementaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HeureSupplementaireService {

    private static final Map<TypeMajoration, Integer> TAUX = Map.of(
            TypeMajoration.T_15, 15,
            TypeMajoration.T_40, 40,
            TypeMajoration.T_60, 60,
            TypeMajoration.T_100, 100
    );

    private final HeureSupplementaireRepository heureSupplementaireRepository;
    private final DossierEmployeRepository dossierEmployeRepository;

    public HeureSupplementaireDto create(HeureSupplementaireDto dto) {
        DossierEmploye employe = dossierEmployeRepository.findById(dto.getEmployeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier employé introuvable : " + dto.getEmployeId()));

        HeureSupplementaire hs = toEntity(dto);
        // Snapshot employé
        hs.setMatricule(employe.getMatricule());
        hs.setNom(employe.getNom());
        hs.setPrenom(employe.getPrenom());
        hs.setDepartement(employe.getDepartement());
        hs.setDateDeclaration(LocalDate.now());
        if (hs.getStatut() == null) hs.setStatut(StatutValidationHS.EN_ATTENTE);

        int taux = TAUX.getOrDefault(hs.getTypeMajoration(), 0);
        hs.setTauxMajoration(taux);
        if (hs.getNombreHeures() != null) {
            hs.setHeuresMajoreesEquivalent(hs.getNombreHeures() * taux / 100.0);
        }

        return toDto(heureSupplementaireRepository.save(hs));
    }

    public HeureSupplementaireDto getById(String id) {
        return toDto(heureSupplementaireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Heure supplémentaire introuvable : " + id)));
    }

    public List<HeureSupplementaireDto> getAll() {
        return heureSupplementaireRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<HeureSupplementaireDto> getByEmployeId(String employeId) {
        return heureSupplementaireRepository.findByEmployeId(employeId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    /**
     * Liste filtrée + paginée pour la façade /api/temps-presences. Filtrage en
     * mémoire ; le champ date unique est confronté à l'intervalle [dateDebut, dateFin].
     */
    public Page<HeureSupplementaireDto> search(
            String employeId, String departement, String statut, String typeMajoration,
            LocalDate dateDebut, LocalDate dateFin, String q, int page, int size) {

        List<HeureSupplementaireDto> filtered = heureSupplementaireRepository.findAll().stream()
                .map(this::toDto)
                .filter(d -> employeId == null || employeId.isBlank() || employeId.equals(d.getEmployeId()))
                .filter(d -> departement == null || departement.isBlank()
                        || (d.getDepartement() != null && d.getDepartement().equalsIgnoreCase(departement)))
                .filter(d -> statut == null || statut.isBlank()
                        || (d.getStatut() != null && d.getStatut().name().equalsIgnoreCase(statut)))
                .filter(d -> typeMajoration == null || typeMajoration.isBlank()
                        || (d.getTypeMajoration() != null && d.getTypeMajoration().name().equalsIgnoreCase(typeMajoration)))
                .filter(d -> dateDebut == null || (d.getDate() != null && !d.getDate().isBefore(dateDebut)))
                .filter(d -> dateFin == null || (d.getDate() != null && !d.getDate().isAfter(dateFin)))
                .filter(d -> matchesQ(q, d.getNom(), d.getPrenom(), d.getMatricule()))
                .sorted(Comparator.comparing(HeureSupplementaireDto::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());

        Pageable pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<HeureSupplementaireDto> content = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        return new PageImpl<>(content, pageable, filtered.size());
    }

    private boolean matchesQ(String q, String nom, String prenom, String matricule) {
        if (q == null || q.isBlank()) return true;
        String s = q.toLowerCase();
        return (nom != null && nom.toLowerCase().contains(s))
                || (prenom != null && prenom.toLowerCase().contains(s))
                || (matricule != null && matricule.toLowerCase().contains(s));
    }

    public HeureSupplementaireDto update(String id, HeureSupplementaireDto dto) {
        HeureSupplementaire existing = heureSupplementaireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Heure supplémentaire introuvable : " + id));
        existing.setDate(dto.getDate());
        existing.setHeureDebut(dto.getHeureDebut());
        existing.setHeureFin(dto.getHeureFin());
        existing.setNombreHeures(dto.getNombreHeures());
        existing.setTypeMajoration(dto.getTypeMajoration());
        existing.setMotif(dto.getMotif());
        int taux = TAUX.getOrDefault(dto.getTypeMajoration(), 0);
        existing.setTauxMajoration(taux);
        if (dto.getNombreHeures() != null) {
            existing.setHeuresMajoreesEquivalent(dto.getNombreHeures() * taux / 100.0);
        }
        return toDto(heureSupplementaireRepository.save(existing));
    }

    public void delete(String id) {
        HeureSupplementaire hs = heureSupplementaireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Heure supplémentaire introuvable : " + id));
        heureSupplementaireRepository.delete(hs);
    }

    public HeureSupplementaireDto valider(String id, String decideurId, String decideurNom, String commentaire) {
        HeureSupplementaire hs = heureSupplementaireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Heure supplémentaire introuvable : " + id));
        hs.setStatut(StatutValidationHS.VALIDEE);
        hs.setDateDecision(LocalDate.now());
        hs.setDecideurId(decideurId);
        hs.setDecideurNom(decideurNom);
        hs.setCommentaireDecision(commentaire);
        return toDto(heureSupplementaireRepository.save(hs));
    }

    public HeureSupplementaireDto refuser(String id, String decideurId, String decideurNom, String commentaire) {
        HeureSupplementaire hs = heureSupplementaireRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Heure supplémentaire introuvable : " + id));
        hs.setStatut(StatutValidationHS.REFUSEE);
        hs.setDateDecision(LocalDate.now());
        hs.setDecideurId(decideurId);
        hs.setDecideurNom(decideurNom);
        hs.setCommentaireDecision(commentaire);
        return toDto(heureSupplementaireRepository.save(hs));
    }

    private HeureSupplementaireDto toDto(HeureSupplementaire e) {
        return HeureSupplementaireDto.builder()
                .id(e.getId()).employeId(e.getEmployeId()).matricule(e.getMatricule())
                .nom(e.getNom()).prenom(e.getPrenom()).departement(e.getDepartement())
                .date(e.getDate()).heureDebut(e.getHeureDebut()).heureFin(e.getHeureFin())
                .nombreHeures(e.getNombreHeures()).typeMajoration(e.getTypeMajoration())
                .tauxMajoration(e.getTauxMajoration()).motif(e.getMotif())
                .heuresMajoreesEquivalent(e.getHeuresMajoreesEquivalent())
                .statut(e.getStatut()).dateDeclaration(e.getDateDeclaration())
                .dateDecision(e.getDateDecision()).decideurId(e.getDecideurId())
                .decideurNom(e.getDecideurNom()).commentaireDecision(e.getCommentaireDecision())
                .build();
    }

    private HeureSupplementaire toEntity(HeureSupplementaireDto dto) {
        return HeureSupplementaire.builder()
                .employeId(dto.getEmployeId()).matricule(dto.getMatricule())
                .nom(dto.getNom()).prenom(dto.getPrenom()).departement(dto.getDepartement())
                .date(dto.getDate()).heureDebut(dto.getHeureDebut()).heureFin(dto.getHeureFin())
                .nombreHeures(dto.getNombreHeures()).typeMajoration(dto.getTypeMajoration())
                .motif(dto.getMotif()).statut(dto.getStatut())
                .build();
    }
}