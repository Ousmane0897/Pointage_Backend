package com.example.Pointage_Cleanic.Mapper.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.LotDto;
import com.example.Pointage_Cleanic.entities.productionchimie.Lot;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LotMapper {
    LotDto toDto(Lot entity);
}
