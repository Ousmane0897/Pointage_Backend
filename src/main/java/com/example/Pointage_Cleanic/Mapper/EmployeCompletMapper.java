package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EmployeCompletMapper {

    @Mapping(target = "photo", ignore = true)
    EmployeComplet toEntity(EmployeCompletDto dto);

    EmployeCompletDto toDto(EmployeComplet entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "photo", ignore = true)
    void updateEntityFromDto(EmployeCompletDto dto, @MappingTarget EmployeComplet entity);
}
