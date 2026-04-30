package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.SanctionDto;
import com.example.Pointage_Cleanic.entities.Sanction;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface SanctionMapper {

    Sanction toEntity(SanctionDto dto);

    SanctionDto toDto(Sanction entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employeId", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "creeParId", ignore = true)
    @Mapping(target = "creeParNom", ignore = true)
    void updateEntityFromDto(SanctionDto dto, @MappingTarget Sanction entity);
}