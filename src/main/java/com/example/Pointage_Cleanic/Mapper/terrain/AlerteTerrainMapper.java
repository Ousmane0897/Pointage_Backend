package com.example.Pointage_Cleanic.Mapper.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AlerteTerrainDto;
import com.example.Pointage_Cleanic.Dto.terrain.ParametresEscaladeDto;
import com.example.Pointage_Cleanic.entities.terrain.AlerteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.ParametresEscalade;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface AlerteTerrainMapper {

    AlerteTerrainDto toDto(AlerteTerrain entity);

    ParametresEscaladeDto toDto(ParametresEscalade entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntityFromDto(ParametresEscaladeDto dto, @MappingTarget ParametresEscalade entity);
}