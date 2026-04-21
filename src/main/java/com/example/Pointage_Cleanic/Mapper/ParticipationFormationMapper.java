package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.ParticipationFormationDto;
import com.example.Pointage_Cleanic.entities.ParticipationFormation;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface ParticipationFormationMapper {

    ParticipationFormation toEntity(ParticipationFormationDto dto);

    ParticipationFormationDto toDto(ParticipationFormation entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "employeId", ignore = true)
    void updateEntityFromDto(ParticipationFormationDto dto, @MappingTarget ParticipationFormation entity);
}