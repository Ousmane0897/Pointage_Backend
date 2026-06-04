package com.example.Pointage_Cleanic.Mapper.rh;
import com.example.Pointage_Cleanic.Mapper.DateMapper;

import com.example.Pointage_Cleanic.Dto.rh.FormationDto;
import com.example.Pointage_Cleanic.entities.rh.Formation;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface FormationMapper {

    @Mapping(target = "dateModification", ignore = true)
    Formation toEntity(FormationDto dto);

    FormationDto toDto(Formation entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateModification", ignore = true)
    void updateEntityFromDto(FormationDto dto, @MappingTarget Formation entity);
}