package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.AjouterAvenantRequest;
import com.example.Pointage_Cleanic.Dto.AlerteContratDto;
import com.example.Pointage_Cleanic.Dto.ContratDto;
import com.example.Pointage_Cleanic.Dto.RenouvellerContratRequest;
import com.example.Pointage_Cleanic.Enum.StatutContrat;
import com.example.Pointage_Cleanic.Mapper.ContratMapper;
import com.example.Pointage_Cleanic.entities.Avenant;
import com.example.Pointage_Cleanic.entities.Contrat;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.entities.Renouvellement;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.ContratRepository;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContratService {

    private final ContratRepository contratRepository;
    private final EmployeCompletRepository employeCompletRepository;
    private final ContratMapper contratMapper;

    public ContratDto create(ContratDto dto) {
        EmployeComplet employe = employeCompletRepository.findById(dto.getEmployeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable : " + dto.getEmployeId()));

        Contrat contrat = contratMapper.toEntity(dto);
        contrat.setEmployeNom(employe.getNom());
        contrat.setEmployePrenom(employe.getPrenom());

        if (contrat.getStatut() == null) {
            contrat.setStatut(StatutContrat.ACTIF);
        }
        if (contrat.getJoursAvantAlerte() == null) {
            contrat.setJoursAvantAlerte(30);
        }

        return contratMapper.toDto(contratRepository.save(contrat));
    }

    public ContratDto getById(String id) {
        return contratMapper.toDto(contratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + id)));
    }

    public List<ContratDto> getAll() {
        return contratRepository.findAll().stream()
                .map(contratMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<ContratDto> getByEmployeId(String employeId) {
        return contratRepository.findByEmployeId(employeId).stream()
                .map(contratMapper::toDto)
                .collect(Collectors.toList());
    }

    public ContratDto update(String id, ContratDto dto) {
        Contrat existing = contratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + id));
        contratMapper.updateEntityFromDto(dto, existing);
        return contratMapper.toDto(contratRepository.save(existing));
    }

    public void delete(String id) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + id));
        contratRepository.delete(contrat);
    }

    public ContratDto renouveler(String id, RenouvellerContratRequest request) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + id));

        Renouvellement renouvellement = Renouvellement.builder()
                .id(UUID.randomUUID().toString())
                .contratId(contrat.getId())
                .ancienneDateFin(contrat.getDateFin())
                .nouvelleDateFin(request.getNouvelleDateFin())
                .dateRenouvellement(LocalDate.now())
                .motif(request.getMotif())
                .build();

        contrat.getRenouvellements().add(renouvellement);
        contrat.setDateFin(request.getNouvelleDateFin());
        contrat.setStatut(StatutContrat.RENOUVELE);

        return contratMapper.toDto(contratRepository.save(contrat));
    }

    public ContratDto ajouterAvenant(String id, AjouterAvenantRequest request) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + id));

        Avenant avenant = Avenant.builder()
                .id(UUID.randomUUID().toString())
                .contratId(contrat.getId())
                .dateCreation(LocalDate.now())
                .objet(request.getObjet())
                .description(request.getDescription())
                .dateEffet(request.getDateEffet())
                .build();

        contrat.getAvenants().add(avenant);

        return contratMapper.toDto(contratRepository.save(contrat));
    }

    public ContratDto resilier(String id) {
        Contrat contrat = contratRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrat introuvable : " + id));
        contrat.setStatut(StatutContrat.RESILIE);
        return contratMapper.toDto(contratRepository.save(contrat));
    }

    public List<AlerteContratDto> getAlertesEcheance() {
        LocalDate today = LocalDate.now();

        return contratRepository.findByStatutAndDateFinIsNotNull(StatutContrat.ACTIF).stream()
                .filter(c -> c.getDateFin().isAfter(today.minusDays(1)))
                .filter(c -> {
                    long joursRestants = ChronoUnit.DAYS.between(today, c.getDateFin());
                    int seuil = c.getJoursAvantAlerte() != null ? c.getJoursAvantAlerte() : 30;
                    return joursRestants <= seuil;
                })
                .map(c -> {
                    long joursRestants = ChronoUnit.DAYS.between(today, c.getDateFin());
                    return AlerteContratDto.builder()
                            .contratId(c.getId())
                            .employeId(c.getEmployeId())
                            .employeNom(c.getEmployeNom())
                            .employePrenom(c.getEmployePrenom())
                            .typeContrat(c.getTypeContrat())
                            .dateFin(c.getDateFin())
                            .joursRestants(joursRestants)
                            .build();
                })
                .sorted(Comparator.comparingLong(AlerteContratDto::getJoursRestants))
                .collect(Collectors.toList());
    }
}