package com.example.Pointage_Cleanic.Mapper.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.CategorieStockDto;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CategorieStockMapper {

    @Mapping(target = "nbEnfants", ignore = true)
    @Mapping(target = "nbProduits", ignore = true)
    CategorieStockDto toDto(CategorieStock entity);
}
