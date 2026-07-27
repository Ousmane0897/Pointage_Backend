package com.example.Pointage_Cleanic.Mapper.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AffectationAgentDto;
import com.example.Pointage_Cleanic.entities.terrain.AffectationAgent;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AffectationAgentMapper {

    AffectationAgentDto toDto(AffectationAgent entity);

    // Les 3 champs d'annulation sont en sortie seule : renseignés par PlanningService.annuler
    // depuis le JWT, jamais depuis le corps d'un POST/PUT (sinon l'auteur serait usurpable).
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "motifAnnulation", ignore = true)
    @Mapping(target = "dateAnnulation", ignore = true)
    @Mapping(target = "annuleParNom", ignore = true)
    AffectationAgent toEntity(AffectationAgentDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "motifAnnulation", ignore = true)
    @Mapping(target = "dateAnnulation", ignore = true)
    @Mapping(target = "annuleParNom", ignore = true)
    void updateEntityFromDto(AffectationAgentDto dto, @MappingTarget AffectationAgent entity);
}