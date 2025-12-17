package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.entities.Employe;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface EmployeMapper {

    // ================================
    //             CREATE
    // ================================
    @Mappings({
            @Mapping(source = "agentId", target = "codeSecret"),
            @Mapping(source = "prenom", target = "prenom"),
            @Mapping(source = "nom", target = "nom"),
            @Mapping(source = "telephone1", target = "numero"),
            @Mapping(source = "poste", target = "intervention"),
            @Mapping(source = "statut", target = "statut"),
            @Mapping(source = "agence", target = "site"),
            @Mapping(source = "joursDeTravail", target = "joursDeTravail"),

            // Horaires simples
            @Mapping(source = "heureDebut", target = "heureDebut"),
            @Mapping(source = "heureFin", target = "heureFin"),

            // Horaires supplémentaires
            @Mapping(source = "heureDebut2", target = "heureDebut2"),
            @Mapping(source = "heureFin2", target = "heureFin2"),

            // Initialisation obligatoire mais NON provenant du DTO
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "employeCreePar", ignore = true),
            @Mapping(target = "siteAvantDeplacement", ignore = true),
            @Mapping(target = "joursDeTravail2", ignore = true),
            @Mapping(target = "matin", ignore = true),
            @Mapping(target = "apresMidi", ignore = true),
            @Mapping(target = "deplacement", ignore = true),
            @Mapping(target = "remplacement", ignore = true),
            @Mapping(target = "heureDebutAvantDeplacement", ignore = true),
            @Mapping(target = "heureFinAvantDeplacement", ignore = true),
            @Mapping(target = "heureDebutAvantDeplacement2", ignore = true),
            @Mapping(target = "heureFinAvantDeplacement2", ignore = true),
            @Mapping(target = "horairesDeRemplacement", ignore = true),
            @Mapping(target = "personneRemplacee", ignore = true),
            @Mapping(target = "dateEtHeureCreation", ignore = true),
            @Mapping(target = "heuresSupplementaires", ignore = true)
    })
    Employe toEmploye(EmployeCompletDto dto);



    // ================================
    //             UPDATE
    // ================================
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mappings({

            // Champs autorisés à être modifiés
            @Mapping(source = "prenom", target = "prenom"),
            @Mapping(source = "nom", target = "nom"),
            @Mapping(source = "telephone1", target = "numero"),
            @Mapping(source = "poste", target = "intervention"),
            @Mapping(source = "statut", target = "statut"),
            @Mapping(source = "agence", target = "site"),
            @Mapping(source = "joursDeTravail", target = "joursDeTravail"),
            @Mapping(source = "heureDebut", target = "heureDebut"),
            @Mapping(source = "heureFin", target = "heureFin"),

            // ❌ CHAMPS OPTIONNELS ou OPÉRATIONNELS NON MODIFIÉS
            @Mapping(target = "joursDeTravail2", ignore = true),
            @Mapping(target = "matin", ignore = true),
            @Mapping(target = "apresMidi", ignore = true),
            @Mapping(target = "deplacement", ignore = true),
            @Mapping(target = "remplacement", ignore = true),
            @Mapping(target = "siteAvantDeplacement", ignore = true),
            @Mapping(target = "heureDebutAvantDeplacement", ignore = true),
            @Mapping(target = "heureFinAvantDeplacement", ignore = true),
            @Mapping(target = "heureDebut2", ignore = true),
            @Mapping(target = "heureFin2", ignore = true),
            @Mapping(target = "heureDebutAvantDeplacement2", ignore = true),
            @Mapping(target = "heureFinAvantDeplacement2", ignore = true),
            @Mapping(target = "horairesDeRemplacement", ignore = true),
            @Mapping(target = "personneRemplacee", ignore = true),
            @Mapping(target = "heuresSupplementaires", ignore = true),
            @Mapping(target = "dateEtHeureCreation", ignore = true),

            // Champs backend
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "codeSecret", ignore = true),
            @Mapping(target = "employeCreePar", ignore = true),

    })
    void updateEmployeFromDto(EmployeCompletDto dto, @MappingTarget Employe employe);
}
