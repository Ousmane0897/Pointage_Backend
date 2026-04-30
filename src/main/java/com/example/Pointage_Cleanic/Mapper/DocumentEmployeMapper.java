package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.DocumentEmployeDto;
import com.example.Pointage_Cleanic.entities.DocumentEmploye;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentEmployeMapper {

    @Mapping(target = "fichierUrl", ignore = true)
    DocumentEmployeDto toDto(DocumentEmploye entity);
}
