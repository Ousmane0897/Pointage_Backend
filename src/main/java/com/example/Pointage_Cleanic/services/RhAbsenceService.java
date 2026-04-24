package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.PieceJustificativeDto;
import com.example.Pointage_Cleanic.Dto.RhAbsenceDto;
import com.example.Pointage_Cleanic.Enum.StatutAbsence;
import com.example.Pointage_Cleanic.Enum.TypeAbsence;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.entities.PieceJustificative;
import com.example.Pointage_Cleanic.entities.RhAbsence;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.RhAbsenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RhAbsenceService {

    private final RhAbsenceRepository rhAbsenceRepository;
    private final DossierEmployeRepository dossierEmployeRepository;

    public RhAbsenceDto create(RhAbsenceDto dto) {
        DossierEmploye employe = dossierEmployeRepository.findById(dto.getEmployeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier employé introuvable : " + dto.getEmployeId()));

        RhAbsence absence = toEntity(dto);
        // Dénormalisation (snapshot employé au moment de l'absence)
        absence.setMatricule(employe.getMatricule());
        absence.setNom(employe.getNom());
        absence.setPrenom(employe.getPrenom());
        absence.setDepartement(employe.getDepartement());

        absence.setNombreJours(computeNombreJours(absence.getDateDebut(), absence.getDateFin()));
        absence.setDateCreation(LocalDate.now());
        if (absence.getStatut() == null) absence.setStatut(StatutAbsence.DECLAREE);

        validerCoherenceType(absence);

        return toDto(rhAbsenceRepository.save(absence));
    }

    public RhAbsenceDto getById(String id) {
        return toDto(requireById(id));
    }

    public List<RhAbsenceDto> getAll() {
        return rhAbsenceRepository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public List<RhAbsenceDto> getByEmployeId(String employeId) {
        return rhAbsenceRepository.findByEmployeId(employeId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    public RhAbsenceDto update(String id, RhAbsenceDto dto) {
        RhAbsence existing = requireById(id);
        existing.setType(dto.getType());
        existing.setTypeAutrePrecision(dto.getTypeAutrePrecision());
        existing.setDateDebut(dto.getDateDebut());
        existing.setDateFin(dto.getDateFin());
        existing.setNombreJours(computeNombreJours(dto.getDateDebut(), dto.getDateFin()));
        existing.setMotif(dto.getMotif());
        if (dto.getStatut() != null) existing.setStatut(dto.getStatut());

        validerCoherenceType(existing);

        return toDto(rhAbsenceRepository.save(existing));
    }

    public void delete(String id) {
        RhAbsence absence = requireById(id);
        rhAbsenceRepository.delete(absence);
    }

    public RhAbsenceDto justifier(String id) {
        RhAbsence absence = requireById(id);
        absence.setStatut(StatutAbsence.JUSTIFIEE);
        return toDto(rhAbsenceRepository.save(absence));
    }

    public RhAbsenceDto uploadJustificatif(String id, MultipartFile file) throws IOException {
        RhAbsence absence = requireById(id);

        PieceJustificative pj = PieceJustificative.builder()
                .id(UUID.randomUUID().toString())
                .nom(file.getOriginalFilename())
                .mimeType(file.getContentType())
                .taille(file.getSize())
                .dateUpload(LocalDate.now())
                .data(file.getBytes())
                .build();

        absence.setPieceJustificative(pj);
        absence.setStatut(StatutAbsence.JUSTIFIEE);
        return toDto(rhAbsenceRepository.save(absence));
    }

    public byte[] downloadJustificatif(String id) {
        RhAbsence absence = requireById(id);
        if (absence.getPieceJustificative() == null || absence.getPieceJustificative().getData() == null) {
            throw new ResourceNotFoundException("Aucun justificatif pour l'absence : " + id);
        }
        return absence.getPieceJustificative().getData();
    }

    private RhAbsence requireById(String id) {
        return rhAbsenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Absence introuvable : " + id));
    }

    /**
     * typeAutrePrecision obligatoire si type=AUTRE ; nettoyé à null sinon
     * pour éviter les données incohérentes persistées.
     */
    private void validerCoherenceType(RhAbsence absence) {
        if (absence.getType() == TypeAbsence.AUTRE) {
            if (absence.getTypeAutrePrecision() == null || absence.getTypeAutrePrecision().isBlank()) {
                throw new IllegalArgumentException(
                        "typeAutrePrecision est obligatoire lorsque type=AUTRE");
            }
        } else {
            absence.setTypeAutrePrecision(null);
        }
    }

    private int computeNombreJours(LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) return 0;
        return (int) ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    private RhAbsenceDto toDto(RhAbsence e) {
        PieceJustificativeDto pjDto = null;
        if (e.getPieceJustificative() != null) {
            PieceJustificative pj = e.getPieceJustificative();
            pjDto = PieceJustificativeDto.builder()
                    .id(pj.getId())
                    .nom(pj.getNom())
                    .mimeType(pj.getMimeType())
                    .taille(pj.getTaille())
                    .dateUpload(pj.getDateUpload())
                    .url("/api/rh/absences/" + e.getId() + "/justificatif")
                    .build();
        }
        return RhAbsenceDto.builder()
                .id(e.getId()).employeId(e.getEmployeId()).matricule(e.getMatricule())
                .nom(e.getNom()).prenom(e.getPrenom()).departement(e.getDepartement())
                .type(e.getType()).typeAutrePrecision(e.getTypeAutrePrecision())
                .dateDebut(e.getDateDebut()).dateFin(e.getDateFin())
                .nombreJours(e.getNombreJours()).motif(e.getMotif()).statut(e.getStatut())
                .pieceJustificative(pjDto).dateCreation(e.getDateCreation()).creeParId(e.getCreeParId())
                .build();
    }

    private RhAbsence toEntity(RhAbsenceDto dto) {
        return RhAbsence.builder()
                .employeId(dto.getEmployeId())
                .type(dto.getType()).typeAutrePrecision(dto.getTypeAutrePrecision())
                .dateDebut(dto.getDateDebut()).dateFin(dto.getDateFin())
                .motif(dto.getMotif()).statut(dto.getStatut()).creeParId(dto.getCreeParId())
                .build();
    }
}
