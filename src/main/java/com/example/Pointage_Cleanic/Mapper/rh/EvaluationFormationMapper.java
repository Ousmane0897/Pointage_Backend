package com.example.Pointage_Cleanic.Mapper.rh;
import com.example.Pointage_Cleanic.Mapper.DateMapper;

import com.example.Pointage_Cleanic.Dto.rh.EvaluationFormationDto;
import com.example.Pointage_Cleanic.entities.rh.EvaluationFormation;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface EvaluationFormationMapper {

    EvaluationFormation toEntity(EvaluationFormationDto dto);

    EvaluationFormationDto toDto(EvaluationFormation entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "participationId", ignore = true)
    @Mapping(target = "sessionId", ignore = true)
    @Mapping(target = "employeId", ignore = true)
    void updateEntityFromDto(EvaluationFormationDto dto, @MappingTarget EvaluationFormation entity);
}