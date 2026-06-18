package com.example.Pointage_Cleanic.Mapper.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ChantierDto;
import com.example.Pointage_Cleanic.entities.stockv2.Chantier;
import org.mapstruct.Mapper;

@Mapper
public interface ChantierMapper {
    ChantierDto toDto(Chantier entity);
}
