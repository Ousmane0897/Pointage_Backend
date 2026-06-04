package com.example.Pointage_Cleanic.Mapper.rh;

import com.example.Pointage_Cleanic.Dto.rh.ParametresPaieDto;
import com.example.Pointage_Cleanic.entities.rh.ParametresPaie;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ParametresPaieMapper {

    ParametresPaie toEntity(ParametresPaieDto dto);

    ParametresPaieDto toDto(ParametresPaie entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(ParametresPaieDto dto, @MappingTarget ParametresPaie entity);
}