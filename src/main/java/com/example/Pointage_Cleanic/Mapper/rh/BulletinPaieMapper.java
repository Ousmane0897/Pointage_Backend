package com.example.Pointage_Cleanic.Mapper.rh;
import com.example.Pointage_Cleanic.Mapper.DateMapper;

import com.example.Pointage_Cleanic.Dto.rh.BulletinPaieDto;
import com.example.Pointage_Cleanic.entities.rh.BulletinPaie;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = DateMapper.class)
public interface BulletinPaieMapper {

    @Mapping(source = "dateCalcul", target = "dateCalcul")
    @Mapping(source = "dateValidation", target = "dateValidation")
    @Mapping(source = "datePaiement", target = "datePaiement")
    BulletinPaie toEntity(BulletinPaieDto dto);

    BulletinPaieDto toDto(BulletinPaie entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "employeId", ignore = true)
    @Mapping(target = "matricule", ignore = true)
    @Mapping(target = "periode", ignore = true)
    @Mapping(target = "lignes", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "dateCalcul", ignore = true)
    @Mapping(target = "dateValidation", ignore = true)
    @Mapping(target = "datePaiement", ignore = true)
    @Mapping(target = "validateurId", ignore = true)
    @Mapping(target = "validateurNom", ignore = true)
    void updateEntityFromDto(BulletinPaieDto dto, @MappingTarget BulletinPaie entity);
}