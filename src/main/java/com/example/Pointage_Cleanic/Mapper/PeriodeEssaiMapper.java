package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.AlertePeriodeEssaiDto;
import com.example.Pointage_Cleanic.Dto.DecisionPeriodeEssaiDto;
import com.example.Pointage_Cleanic.Dto.PeriodeEssaiDto;
import com.example.Pointage_Cleanic.entities.AlertePeriodeEssai;
import com.example.Pointage_Cleanic.entities.DecisionPeriodeEssai;
import com.example.Pointage_Cleanic.entities.PeriodeEssai;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PeriodeEssaiMapper {

    PeriodeEssaiDto toDto(PeriodeEssai entity);

    AlertePeriodeEssaiDto toDto(AlertePeriodeEssai entity);

    DecisionPeriodeEssaiDto toDto(DecisionPeriodeEssai entity);

    List<PeriodeEssaiDto> toDtoList(List<PeriodeEssai> entities);
}