package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import org.mapstruct.*;


@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface EmployeCompletMapper {

    // ================================
    //            CREATE
    // ================================
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "nomComplet", ignore = true)
    @Mapping(source = "dateNaissance", target = "dateNaissance")
    @Mapping(source = "dateEmbauche", target = "dateEmbauche")
    @Mapping(source = "dateFinContrat", target = "dateFinContrat")
    @Mapping(source = "dateSortie", target = "dateSortie")
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
    @Mapping(source = "dateNaissance", target = "dateNaissance")
    @Mapping(source = "dateEmbauche", target = "dateEmbauche")
    @Mapping(source = "dateFinContrat", target = "dateFinContrat")
    @Mapping(source = "dateSortie", target = "dateSortie")
    void updateEntityFromDto(EmployeCompletDto dto, @MappingTarget EmployeComplet entity);

}
