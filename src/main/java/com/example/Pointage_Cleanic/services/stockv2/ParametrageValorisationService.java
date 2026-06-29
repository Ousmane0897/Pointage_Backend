package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ParametrageValorisationDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ParametrageValorisationPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.entities.stockv2.ParametrageValorisation;
import com.example.Pointage_Cleanic.repositories.stockv2.ParametrageValorisationRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Paramétrage global de la valorisation (Stock v2 7.6). Singleton lu/écrit par id fixe.
 * Source de la méthode de valorisation par défaut (héritée par les produits sans override).
 */
@Service
@RequiredArgsConstructor
public class ParametrageValorisationService {

    private final ParametrageValorisationRepository repository;
    private final CurrentUserProvider currentUser;

    /** Renvoie le paramétrage, en créant un défaut {@code FIXE} à la volée s'il n'existe pas. */
    public ParametrageValorisation charger() {
        return repository.findById(ParametrageValorisation.SINGLETON_ID)
                .orElseGet(() -> ParametrageValorisation.builder()
                        .id(ParametrageValorisation.SINGLETON_ID)
                        .methodeDefaut(MethodeValorisation.FIXE)
                        .build());
    }

    /** Méthode globale par défaut (peut être null si jamais paramétrée). */
    public MethodeValorisation methodeDefaut() {
        return repository.findById(ParametrageValorisation.SINGLETON_ID)
                .map(ParametrageValorisation::getMethodeDefaut)
                .orElse(null);
    }

    public ParametrageValorisationDto get() {
        return toDto(charger());
    }

    public ParametrageValorisationDto update(ParametrageValorisationPayload payload) {
        if (payload == null || payload.getMethodeDefaut() == null) {
            throw new IllegalArgumentException("La méthode de valorisation par défaut est obligatoire");
        }
        ParametrageValorisation parametrage = charger();
        parametrage.setId(ParametrageValorisation.SINGLETON_ID);
        parametrage.setMethodeDefaut(payload.getMethodeDefaut());
        parametrage.setUpdatedAt(LocalDateTime.now());
        parametrage.setUpdatedBy(currentUser.currentUserNom());
        return toDto(repository.save(parametrage));
    }

    private ParametrageValorisationDto toDto(ParametrageValorisation entity) {
        return ParametrageValorisationDto.builder()
                .methodeDefaut(entity.getMethodeDefaut())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .build();
    }
}
