package com.example.Pointage_Cleanic.Mapper.rh;

import com.example.Pointage_Cleanic.Dto.rh.AlertePeriodeEssaiDto;
import com.example.Pointage_Cleanic.Dto.rh.DecisionPeriodeEssaiDto;
import com.example.Pointage_Cleanic.Dto.rh.PeriodeEssaiDto;
import com.example.Pointage_Cleanic.entities.rh.AlertePeriodeEssai;
import com.example.Pointage_Cleanic.entities.rh.DecisionPeriodeEssai;
import com.example.Pointage_Cleanic.entities.rh.PeriodeEssai;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PeriodeEssaiMapper {

    PeriodeEssaiDto toDto(PeriodeEssai entity);

    AlertePeriodeEssaiDto toDto(AlertePeriodeEssai entity);

    DecisionPeriodeEssaiDto toDto(DecisionPeriodeEssai entity);

    List<PeriodeEssaiDto> toDtoList(List<PeriodeEssai> entities);
}