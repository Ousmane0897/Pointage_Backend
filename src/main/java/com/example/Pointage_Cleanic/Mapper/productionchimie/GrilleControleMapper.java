package com.example.Pointage_Cleanic.Mapper.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.GrilleControleDto;
import com.example.Pointage_Cleanic.entities.productionchimie.GrilleControle;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface GrilleControleMapper {

    GrilleControleDto toDto(GrilleControle entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    GrilleControle toEntity(GrilleControleDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(GrilleControleDto dto, @MappingTarget GrilleControle entity);
}
