package com.example.Pointage_Cleanic.Mapper.rh;
import com.example.Pointage_Cleanic.Mapper.DateMapper;

import com.example.Pointage_Cleanic.Dto.rh.GrilleEvaluationDto;
import com.example.Pointage_Cleanic.entities.rh.GrilleEvaluation;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface GrilleEvaluationMapper {

    GrilleEvaluation toEntity(GrilleEvaluationDto dto);

    GrilleEvaluationDto toDto(GrilleEvaluation entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    void updateEntityFromDto(GrilleEvaluationDto dto, @MappingTarget GrilleEvaluation entity);
}