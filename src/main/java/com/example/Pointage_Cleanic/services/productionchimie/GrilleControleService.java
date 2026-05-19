package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.GrilleControleDto;
import com.example.Pointage_Cleanic.Mapper.productionchimie.GrilleControleMapper;
import com.example.Pointage_Cleanic.entities.productionchimie.GrilleControle;
import com.example.Pointage_Cleanic.entities.productionchimie.Lot;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.productionchimie.GrilleControleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GrilleControleService {

    private final GrilleControleRepository repository;
    private final GrilleControleMapper mapper;
    private final LotService lotService;

    public List<GrilleControleDto> list() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    public GrilleControleDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public GrilleControle loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grille de contrôle introuvable : " + id));
    }

    public GrilleControleDto pourLot(String lotId) {
        Lot lot = lotService.loadOrThrow(lotId);
        if (lot.getFormulationId() != null) {
            GrilleControle grille = repository.findFirstByFormulationId(lot.getFormulationId()).orElse(null);
            if (grille != null) return mapper.toDto(grille);
        }
        if (lot.getProduitNom() != null) {
            List<GrilleControle> grilles = repository.findByProduitNom(lot.getProduitNom());
            if (!grilles.isEmpty()) return mapper.toDto(grilles.get(0));
        }
        return null;
    }

    public GrilleControleDto create(GrilleControleDto dto) {
        GrilleControle entity = mapper.toEntity(dto);
        entity.setId(null);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return mapper.toDto(repository.save(entity));
    }

    public GrilleControleDto update(String id, GrilleControleDto dto) {
        GrilleControle entity = loadOrThrow(id);
        mapper.updateEntityFromDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(entity));
    }

    public void delete(String id) {
        repository.delete(loadOrThrow(id));
    }
}
