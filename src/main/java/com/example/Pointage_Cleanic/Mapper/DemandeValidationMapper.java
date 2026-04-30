package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.DemandeValidationDto;
import com.example.Pointage_Cleanic.entities.DemandeValidationPeriodeEssai;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DemandeValidationMapper {

    DemandeValidationDto toDto(DemandeValidationPeriodeEssai entity);

    List<DemandeValidationDto> toDtoList(List<DemandeValidationPeriodeEssai> entities);
}