package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Dto.rh.ParametresPaieDto;
import com.example.Pointage_Cleanic.Mapper.rh.ParametresPaieMapper;
import com.example.Pointage_Cleanic.entities.rh.ParametresPaie;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.rh.ParametresPaieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ParametresPaieService {

    private final ParametresPaieRepository parametresPaieRepository;
    private final ParametresPaieMapper parametresPaieMapper;

    public ParametresPaie loadEntity() {
        return parametresPaieRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Paramètres de paie non initialisés. Vérifiez le DataLoader."));
    }

    public ParametresPaieDto get() {
        return parametresPaieMapper.toDto(loadEntity());
    }

    public ParametresPaieDto update(ParametresPaieDto dto, String modifieParId, String modifieParNom) {
        ParametresPaie existing = loadEntity();
        parametresPaieMapper.updateEntityFromDto(dto, existing);
        existing.setDateModification(Instant.now());
        existing.setModifieParId(modifieParId);
        existing.setModifieParNom(modifieParNom);
        return parametresPaieMapper.toDto(parametresPaieRepository.save(existing));
    }
}