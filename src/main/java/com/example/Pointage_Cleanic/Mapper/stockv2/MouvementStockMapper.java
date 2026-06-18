package com.example.Pointage_Cleanic.Mapper.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MouvementStockDto;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import org.mapstruct.Mapper;

@Mapper
public interface MouvementStockMapper {
    MouvementStockDto toDto(MouvementStock entity);
}
