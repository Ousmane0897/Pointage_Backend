package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.ParametresPaieDto;
import com.example.Pointage_Cleanic.entities.ParametresPaie;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ParametresPaieMapper {

    ParametresPaie toEntity(ParametresPaieDto dto);

    ParametresPaieDto toDto(ParametresPaie entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(ParametresPaieDto dto, @MappingTarget ParametresPaie entity);
}