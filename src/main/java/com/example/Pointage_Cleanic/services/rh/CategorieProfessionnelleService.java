package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.CategorieProfessionnelleDto;
import com.example.Pointage_Cleanic.Enum.rh.RegimeIpres;
import com.example.Pointage_Cleanic.Mapper.rh.CategorieProfessionnelleMapper;
import com.example.Pointage_Cleanic.entities.rh.CategorieProfessionnelle;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.CategorieProfessionnelleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategorieProfessionnelleService {

    private final CategorieProfessionnelleRepository categorieProfessionnelleRepository;
    private final CategorieProfessionnelleMapper categorieProfessionnelleMapper;

    public CategorieProfessionnelleDto create(CategorieProfessionnelleDto dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new IllegalArgumentException("Le code de la catégorie est requis.");
        }
        if (categorieProfessionnelleRepository.existsByCode(dto.getCode())) {
            throw new IllegalArgumentException("Une catégorie avec le code '" + dto.getCode() + "' existe déjà.");
        }

        CategorieProfessionnelle entity = categorieProfessionnelleMapper.toEntity(dto);
        entity.setDateCreation(Instant.now());
        entity.setDateModification(Instant.now());
        return categorieProfessionnelleMapper.toDto(categorieProfessionnelleRepository.save(entity));
    }

    public CategorieProfessionnelleDto getById(String id) {
        return categorieProfessionnelleMapper.toDto(categorieProfessionnelleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie introuvable : " + id)));
    }

    public List<CategorieProfessionnelleDto> getAll(String q, RegimeIpres regimeIpres, Boolean actif) {
        List<CategorieProfessionnelle> all = categorieProfessionnelleRepository.findAll();
        return all.stream()
                .filter(c -> actif == null || c.isActif() == actif)
                .filter(c -> regimeIpres == null || c.getRegimeIpres() == regimeIpres)
                .filter(c -> {
                    if (q == null || q.isBlank()) return true;
                    String needle = q.toLowerCase();
                    return (c.getCode() != null && c.getCode().toLowerCase().contains(needle))
                            || (c.getLibelle() != null && c.getLibelle().toLowerCase().contains(needle));
                })
                .map(categorieProfessionnelleMapper::toDto)
                .collect(Collectors.toList());
    }

    public CategorieProfessionnelleDto update(String id, CategorieProfessionnelleDto dto) {
        CategorieProfessionnelle existing = categorieProfessionnelleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie introuvable : " + id));
        categorieProfessionnelleMapper.updateEntityFromDto(dto, existing);
        existing.setDateModification(Instant.now());
        return categorieProfessionnelleMapper.toDto(categorieProfessionnelleRepository.save(existing));
    }

    public void delete(String id) {
        CategorieProfessionnelle cat = categorieProfessionnelleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie introuvable : " + id));
        categorieProfessionnelleRepository.delete(cat);
    }
}