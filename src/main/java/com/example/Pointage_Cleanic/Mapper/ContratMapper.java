package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.ContratDto;
import com.example.Pointage_Cleanic.entities.Contrat;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface ContratMapper {

    @Mapping(source = "dateDebut", target = "dateDebut")
    @Mapping(source = "dateFin", target = "dateFin")
    @Mapping(target = "fichierContrat", ignore = true)
    Contrat toEntity(ContratDto dto);

    @Mapping(target = "fichierContratUrl", ignore = true)
    ContratDto toDto(Contrat entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(source = "dateDebut", target = "dateDebut")
    @Mapping(source = "dateFin", target = "dateFin")
    @Mapping(target = "renouvellements", ignore = true)
    @Mapping(target = "avenants", ignore = true)
    @Mapping(target = "employeId", ignore = true)
    @Mapping(target = "fichierContrat", ignore = true)
    void updateEntityFromDto(ContratDto dto, @MappingTarget Contrat entity);
}