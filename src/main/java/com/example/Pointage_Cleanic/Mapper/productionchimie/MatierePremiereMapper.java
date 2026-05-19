package com.example.Pointage_Cleanic.Mapper.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.MatierePremiereDto;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface MatierePremiereMapper {

    @Mapping(target = "ficheSecuriteUrl", source = "entity", qualifiedByName = "buildUrl")
    MatierePremiereDto toDto(MatierePremiere entity);

    @Mapping(target = "ficheSecurite", ignore = true)
    @Mapping(target = "ficheSecuriteNom", ignore = true)
    @Mapping(target = "ficheSecuriteMimeType", ignore = true)
    @Mapping(target = "ficheSecuriteTaille", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MatierePremiere toEntity(MatierePremiereDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ficheSecurite", ignore = true)
    @Mapping(target = "ficheSecuriteNom", ignore = true)
    @Mapping(target = "ficheSecuriteMimeType", ignore = true)
    @Mapping(target = "ficheSecuriteTaille", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(MatierePremiereDto dto, @MappingTarget MatierePremiere entity);

    @Named("buildUrl")
    default String buildFicheSecuriteUrl(MatierePremiere entity) {
        if (entity == null || entity.getFicheSecurite() == null || entity.getId() == null) {
            return null;
        }
        return "/api/production-chimie/stock-chimie/matieres-premieres/" + entity.getId() + "/fiche-securite";
    }
}