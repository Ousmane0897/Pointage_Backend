package com.example.Pointage_Cleanic.Mapper.rh;
import com.example.Pointage_Cleanic.Mapper.DateMapper;

import com.example.Pointage_Cleanic.Dto.rh.DeclarationSocialeDto;
import com.example.Pointage_Cleanic.entities.rh.DeclarationSociale;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface DeclarationSocialeMapper {

    @Mapping(source = "dateGeneration", target = "dateGeneration")
    @Mapping(source = "dateTransmission", target = "dateTransmission")
    DeclarationSociale toEntity(DeclarationSocialeDto dto);

    DeclarationSocialeDto toDto(DeclarationSociale entity);
}