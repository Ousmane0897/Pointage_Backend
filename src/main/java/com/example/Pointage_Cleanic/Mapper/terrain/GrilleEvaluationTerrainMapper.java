package com.example.Pointage_Cleanic.Mapper.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.GrilleEvaluationTerrainDto;
import com.example.Pointage_Cleanic.entities.terrain.GrilleEvaluationTerrain;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface GrilleEvaluationTerrainMapper {

    GrilleEvaluationTerrainDto toDto(GrilleEvaluationTerrain entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    GrilleEvaluationTerrain toEntity(GrilleEvaluationTerrainDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(GrilleEvaluationTerrainDto dto, @MappingTarget GrilleEvaluationTerrain entity);
}