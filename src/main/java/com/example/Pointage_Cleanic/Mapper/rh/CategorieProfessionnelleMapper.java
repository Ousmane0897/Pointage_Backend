package com.example.Pointage_Cleanic.Mapper.rh;

import com.example.Pointage_Cleanic.Dto.rh.CategorieProfessionnelleDto;
import com.example.Pointage_Cleanic.entities.rh.CategorieProfessionnelle;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface CategorieProfessionnelleMapper {

    CategorieProfessionnelle toEntity(CategorieProfessionnelleDto dto);

    CategorieProfessionnelleDto toDto(CategorieProfessionnelle entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    void updateEntityFromDto(CategorieProfessionnelleDto dto, @MappingTarget CategorieProfessionnelle entity);
}