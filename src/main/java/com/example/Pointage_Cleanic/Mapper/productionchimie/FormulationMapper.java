package com.example.Pointage_Cleanic.Mapper.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.FicheFormulationDto;
import com.example.Pointage_Cleanic.entities.productionchimie.FicheFormulation;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface FormulationMapper {

    @Mapping(target = "motif", ignore = true)
    @Mapping(target = "synthese", ignore = true)
    FicheFormulationDto toDto(FicheFormulation entity);

    @Mapping(target = "versions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "versionCourante", ignore = true)
    FicheFormulation toEntity(FicheFormulationDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "versions", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "versionCourante", ignore = true)
    void updateEntityFromDto(FicheFormulationDto dto, @MappingTarget FicheFormulation entity);
}