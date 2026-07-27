package com.example.Pointage_Cleanic.Mapper.rh;
import com.example.Pointage_Cleanic.Mapper.DateMapper;

import com.example.Pointage_Cleanic.Dto.rh.AffectationSiteDto;
import com.example.Pointage_Cleanic.Dto.rh.ContactUrgenceDto;
import com.example.Pointage_Cleanic.Dto.rh.DossierEmployeDto;
import com.example.Pointage_Cleanic.entities.rh.AffectationSite;
import com.example.Pointage_Cleanic.entities.rh.ContactUrgence;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface DossierEmployeMapper {

    @Mapping(target = "photo", ignore = true)
    DossierEmploye toEntity(DossierEmployeDto dto);

    @Mapping(target = "photoUrl", ignore = true)
    DossierEmployeDto toDto(DossierEmploye entity);

    // affectations + siteAffecte sont pilotés explicitement par le service
    // (remplacement complet de la liste + dérivation de siteAffecte), pas par
    // le merge partiel : on les ignore ici pour éviter les surprises de merge
    // de collection MapStruct.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "affectations", ignore = true)
    @Mapping(target = "siteAffecte", ignore = true)
    void updateEntityFromDto(DossierEmployeDto dto, @MappingTarget DossierEmploye entity);

    ContactUrgence toEntity(ContactUrgenceDto dto);

    ContactUrgenceDto toDto(ContactUrgence entity);

    AffectationSite toEntity(AffectationSiteDto dto);

    AffectationSiteDto toDto(AffectationSite entity);

    List<AffectationSite> toAffectationEntities(List<AffectationSiteDto> dtos);
}