package com.example.Pointage_Cleanic.Mapper.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.MouvementStockChimieDto;
import com.example.Pointage_Cleanic.entities.productionchimie.MouvementStockChimie;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MouvementStockChimieMapper {

    MouvementStockChimieDto toDto(MouvementStockChimie entity);

    MouvementStockChimie toEntity(MouvementStockChimieDto dto);
}