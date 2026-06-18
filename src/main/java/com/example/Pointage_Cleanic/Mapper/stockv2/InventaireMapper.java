package com.example.Pointage_Cleanic.Mapper.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.InventaireDto;
import com.example.Pointage_Cleanic.Dto.stockv2.LigneInventaireDto;
import com.example.Pointage_Cleanic.entities.stockv2.Inventaire;
import com.example.Pointage_Cleanic.entities.stockv2.LigneInventaire;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface InventaireMapper {
    InventaireDto toDto(Inventaire entity);
    LigneInventaireDto toDto(LigneInventaire ligne);
    List<LigneInventaireDto> toLigneDtos(List<LigneInventaire> lignes);
}
