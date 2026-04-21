package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.BesoinFormationDto;
import com.example.Pointage_Cleanic.Enum.PrioriteBesoin;
import com.example.Pointage_Cleanic.Enum.StatutBesoin;
import com.example.Pointage_Cleanic.Mapper.BesoinFormationMapper;
import com.example.Pointage_Cleanic.entities.BesoinFormation;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.BesoinFormationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BesoinFormationService {

    private final BesoinFormationRepository besoinFormationRepository;
    private final BesoinFormationMapper besoinFormationMapper;

    public BesoinFormationDto create(BesoinFormationDto dto) {
        BesoinFormation entity = besoinFormationMapper.toEntity(dto);
        if (entity.getStatut() == null) entity.setStatut(StatutBesoin.IDENTIFIE);
        if (entity.getDateIdentification() == null) entity.setDateIdentification(LocalDate.now());
        return besoinFormationMapper.toDto(besoinFormationRepository.save(entity));
    }

    public BesoinFormationDto getById(String id) {
        return besoinFormationMapper.toDto(findEntity(id));
    }

    public List<BesoinFormationDto> search(String departement, PrioriteBesoin priorite, StatutBesoin statut) {
        List<BesoinFormation> list;
        if (departement != null && statut != null && priorite != null) {
            list = besoinFormationRepository.findByDepartementAndStatutAndPriorite(departement, statut, priorite);
        } else if (departement != null && statut != null) {
            list = besoinFormationRepository.findByDepartementAndStatut(departement, statut);
        } else if (departement != null && priorite != null) {
            list = besoinFormationRepository.findByDepartementAndPriorite(departement, priorite);
        } else if (departement != null) {
            list = besoinFormationRepository.findByDepartement(departement);
        } else if (statut != null) {
            list = besoinFormationRepository.findByStatut(statut);
        } else if (priorite != null) {
            list = besoinFormationRepository.findByPriorite(priorite);
        } else {
            list = besoinFormationRepository.findAll();
        }
        return list.stream().map(besoinFormationMapper::toDto).collect(Collectors.toList());
    }

    public List<BesoinFormationDto> getByEmploye(String employeId) {
        return besoinFormationRepository.findByEmployeId(employeId).stream()
                .map(besoinFormationMapper::toDto).collect(Collectors.toList());
    }

    public BesoinFormationDto update(String id, BesoinFormationDto dto) {
        BesoinFormation existing = findEntity(id);
        besoinFormationMapper.updateEntityFromDto(dto, existing);
        return besoinFormationMapper.toDto(besoinFormationRepository.save(existing));
    }

    public BesoinFormationDto changerStatut(String id, StatutBesoin statut) {
        BesoinFormation existing = findEntity(id);
        existing.setStatut(statut);
        return besoinFormationMapper.toDto(besoinFormationRepository.save(existing));
    }

    public void delete(String id) {
        besoinFormationRepository.delete(findEntity(id));
    }

    private BesoinFormation findEntity(String id) {
        return besoinFormationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Besoin de formation introuvable : " + id));
    }
}