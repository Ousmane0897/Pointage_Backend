package com.example.Pointage_Cleanic.Mapper.rh;

import com.example.Pointage_Cleanic.Dto.rh.DocumentEmployeDto;
import com.example.Pointage_Cleanic.entities.rh.DocumentEmploye;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentEmployeMapper {

    @Mapping(target = "fichierUrl", ignore = true)
    DocumentEmployeDto toDto(DocumentEmploye entity);
}
