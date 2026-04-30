package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.DeclarationSocialeDto;
import com.example.Pointage_Cleanic.entities.DeclarationSociale;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface DeclarationSocialeMapper {

    @Mapping(source = "dateGeneration", target = "dateGeneration")
    @Mapping(source = "dateTransmission", target = "dateTransmission")
    DeclarationSociale toEntity(DeclarationSocialeDto dto);

    DeclarationSocialeDto toDto(DeclarationSociale entity);
}