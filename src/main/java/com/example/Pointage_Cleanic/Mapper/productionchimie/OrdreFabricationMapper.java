package com.example.Pointage_Cleanic.Mapper.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.OrdreFabricationDto;
import com.example.Pointage_Cleanic.entities.productionchimie.OrdreFabrication;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface OrdreFabricationMapper {

    OrdreFabricationDto toDto(OrdreFabrication entity);

    @Mapping(target = "numero", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "historiqueStatuts", ignore = true)
    @Mapping(target = "consommationMp", ignore = true)
    @Mapping(target = "dateLancementEffective", ignore = true)
    @Mapping(target = "dateFin", ignore = true)
    @Mapping(target = "lotGenereId", ignore = true)
    @Mapping(target = "lotGenereNumero", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OrdreFabrication toEntity(OrdreFabricationDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "numero", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "historiqueStatuts", ignore = true)
    @Mapping(target = "consommationMp", ignore = true)
    @Mapping(target = "dateLancementEffective", ignore = true)
    @Mapping(target = "dateFin", ignore = true)
    @Mapping(target = "lotGenereId", ignore = true)
    @Mapping(target = "lotGenereNumero", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(OrdreFabricationDto dto, @MappingTarget OrdreFabrication entity);
}