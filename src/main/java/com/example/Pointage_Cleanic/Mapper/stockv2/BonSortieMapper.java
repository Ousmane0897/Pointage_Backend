package com.example.Pointage_Cleanic.Mapper.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonSortieDto;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import org.mapstruct.Mapper;

@Mapper
public interface BonSortieMapper {
    BonSortieDto toDto(BonSortie entity);
}
