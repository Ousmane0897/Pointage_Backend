package com.example.Pointage_Cleanic.Mapper.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.SiteClientDto;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface SiteClientMapper {

    @Mapping(target = "cahierDesChargesUrl", source = "entity", qualifiedByName = "buildCahierUrl")
    SiteClientDto toDto(SiteClient entity);

    @Mapping(target = "cahierDesChargesFichier", ignore = true)
    @Mapping(target = "cahierDesChargesNomFichier", ignore = true)
    @Mapping(target = "cahierDesChargesMimeType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    SiteClient toEntity(SiteClientDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cahierDesChargesFichier", ignore = true)
    @Mapping(target = "cahierDesChargesNomFichier", ignore = true)
    @Mapping(target = "cahierDesChargesMimeType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(SiteClientDto dto, @MappingTarget SiteClient entity);

    @Named("buildCahierUrl")
    default String buildCahierUrl(SiteClient entity) {
        if (entity == null || entity.getCahierDesChargesFichier() == null || entity.getId() == null) {
            return null;
        }
        return "/api/terrain/sites-clients/" + entity.getId() + "/cahier-charges";
    }
}