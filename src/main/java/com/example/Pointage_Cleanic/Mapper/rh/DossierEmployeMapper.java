package com.example.Pointage_Cleanic.Mapper.rh;
import com.example.Pointage_Cleanic.Mapper.DateMapper;

import com.example.Pointage_Cleanic.Dto.rh.ContactUrgenceDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.entities.rh.ContactUrgence;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
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