package com.example.Pointage_Cleanic.Mapper.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonSortieDto;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface BonSortieMapper {
    @Mapping(target = "libelleNumero", expression = "java(entity.libelleNumero())")
    @Mapping(target = "annulation", expression = "java(entity.getPiece() == null ? null : entity.getPiece().getAnnulation())")
    @Mapping(target = "abandon", expression = "java(entity.getPiece() == null ? null : entity.getPiece().getAbandon())")
    BonSortieDto toDto(BonSortie entity);
}
