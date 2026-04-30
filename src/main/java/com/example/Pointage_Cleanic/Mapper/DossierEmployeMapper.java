package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.ContactUrgenceDto;
import com.example.Pointage_Cleanic.Dto.DossierEmployeDto;
import com.example.Pointage_Cleanic.entities.ContactUrgence;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface DossierEmployeMapper {

    @Mapping(target = "photo", ignore = true)
    DossierEmploye toEntity(DossierEmployeDto dto);

    @Mapping(target = "photoUrl", ignore = true)
    DossierEmployeDto toDto(DossierEmploye entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(DossierEmployeDto dto, @MappingTarget DossierEmploye entity);

    ContactUrgence toEntity(ContactUrgenceDto dto);

    ContactUrgenceDto toDto(ContactUrgence entity);
}