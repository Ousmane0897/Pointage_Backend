package com.example.Pointage_Cleanic.Mapper.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.PlafondDto;
import com.example.Pointage_Cleanic.entities.stockv2.Plafond;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PlafondMapper {

    // siteNom / cibleLibelle / unite sont dénormalisés par le service après mapping.
    @Mapping(target = "siteNom", ignore = true)
    @Mapping(target = "cibleLibelle", ignore = true)
    @Mapping(target = "unite", ignore = true)
    PlafondDto toDto(Plafond entity);
}
