package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EmployeCompletMapper {

    // ================================
    //            CREATE
    // ================================
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "nomComplet", ignore = true)  // nouveau champ généré dans le service
    EmployeComplet toEntity(EmployeCompletDto dto);


    // ================================
    //             DTO
    // ================================
    EmployeCompletDto toDto(EmployeComplet entity);


    // ================================
    //             UPDATE
    // ================================
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "nomComplet", ignore = true)
    void updateEntityFromDto(EmployeCompletDto dto, @MappingTarget EmployeComplet entity);

}
