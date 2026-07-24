package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ParametresProductionChimieDto;
import com.example.Pointage_Cleanic.entities.productionchimie.ParametresProductionChimie;
import com.example.Pointage_Cleanic.repositories.productionchimie.ParametresProductionChimieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Paramétrage global du module Production Chimie (document singleton).
 * Get-or-create paresseux avec valeur par défaut, comme le paramétrage d'escalade terrain.
 */
@Service
@RequiredArgsConstructor
public class ParametresProductionChimieService {

    /** Tolérance de contrôle du total par défaut (± 0,1 %). */
    public static final double TOLERANCE_TOTAL_DEFAUT_PCT = 0.1;

    private final ParametresProductionChimieRepository repository;

    public ParametresProductionChimieDto getParametres() {
        return toDto(getOrCreate());
    }

    public ParametresProductionChimieDto updateParametres(ParametresProductionChimieDto dto) {
        ParametresProductionChimie entity = getOrCreate();
        if (dto.getToleranceTotalPct() != null) {
            entity.setToleranceTotalPct(dto.getToleranceTotalPct());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setUpdatedBy(currentUser());
        return toDto(repository.save(entity));
    }

    /** Entité singleton, créée avec la tolérance par défaut si absente. */
    public ParametresProductionChimie getOrCreate() {
        return repository.findAll().stream().findFirst()
                .orElseGet(() -> repository.save(ParametresProductionChimie.builder()
                        .toleranceTotalPct(TOLERANCE_TOTAL_DEFAUT_PCT)
                        .updatedAt(LocalDateTime.now())
                        .updatedBy("system")
                        .build()));
    }

    private ParametresProductionChimieDto toDto(ParametresProductionChimie e) {
        return ParametresProductionChimieDto.builder()
                .id(e.getId())
                .toleranceTotalPct(e.getToleranceTotalPct())
                .updatedAt(e.getUpdatedAt())
                .updatedBy(e.getUpdatedBy())
                .build();
    }

    private String currentUser() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(a -> a.getName())
                .filter(name -> !"anonymousUser".equals(name))
                .orElse("system");
    }
}
