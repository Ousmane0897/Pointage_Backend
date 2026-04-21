package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.EvaluationPeriodiqueDto;
import com.example.Pointage_Cleanic.entities.EvaluationPeriodique;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface EvaluationPeriodiqueMapper {

    EvaluationPeriodique toEntity(EvaluationPeriodiqueDto dto);

    EvaluationPeriodiqueDto toDto(EvaluationPeriodique entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employeId", ignore = true)
    @Mapping(target = "grilleId", ignore = true)
    @Mapping(target = "periode", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "dateCreation", ignore = true)
    @Mapping(target = "dateAutoEvaluation", ignore = true)
    @Mapping(target = "dateEvaluationManager", ignore = true)
    @Mapping(target = "dateValidation", ignore = true)
    @Mapping(target = "noteGlobale", ignore = true)
    @Mapping(target = "noteAlphabetique", ignore = true)
    void updateEntityFromDto(EvaluationPeriodiqueDto dto, @MappingTarget EvaluationPeriodique entity);
}