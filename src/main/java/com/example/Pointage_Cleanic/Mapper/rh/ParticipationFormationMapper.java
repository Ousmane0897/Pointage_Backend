package com.example.Pointage_Cleanic.Mapper.rh;
import com.example.Pointage_Cleanic.Mapper.DateMapper;

import com.example.Pointage_Cleanic.Dto.rh.ParticipationFormationDto;
import com.example.Pointage_Cleanic.entities.rh.ParticipationFormation;
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