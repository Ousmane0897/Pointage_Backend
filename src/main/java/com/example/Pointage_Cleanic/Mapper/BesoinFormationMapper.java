package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.BesoinFormationDto;
import com.example.Pointage_Cleanic.entities.BesoinFormation;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface BesoinFormationMapper {

    BesoinFormation toEntity(BesoinFormationDto dto);

    BesoinFormationDto toDto(BesoinFormation entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employeId", ignore = true)
    @Mapping(target = "dateIdentification", ignore = true)
    void updateEntityFromDto(BesoinFormationDto dto, @MappingTarget BesoinFormation entity);
}