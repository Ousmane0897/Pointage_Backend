package com.example.Pointage_Cleanic.Mapper.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.PointageTerrainDto;
import com.example.Pointage_Cleanic.entities.terrain.PointageTerrain;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PointageTerrainMapper {

    PointageTerrainDto toDto(PointageTerrain entity);

    @Mapping(target = "createdAt", ignore = true)
    PointageTerrain toEntity(PointageTerrainDto dto);
}