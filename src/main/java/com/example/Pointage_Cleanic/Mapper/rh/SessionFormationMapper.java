package com.example.Pointage_Cleanic.Mapper.rh;
import com.example.Pointage_Cleanic.Mapper.DateMapper;

import com.example.Pointage_Cleanic.Dto.rh.SessionFormationDto;
import com.example.Pointage_Cleanic.entities.rh.SessionFormation;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface SessionFormationMapper {

    SessionFormation toEntity(SessionFormationDto dto);

    SessionFormationDto toDto(SessionFormation entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "formationId", ignore = true)
    @Mapping(target = "participantsInscrits", ignore = true)
    void updateEntityFromDto(SessionFormationDto dto, @MappingTarget SessionFormation entity);
}