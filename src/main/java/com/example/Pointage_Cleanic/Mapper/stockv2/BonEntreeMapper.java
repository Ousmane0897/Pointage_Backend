package com.example.Pointage_Cleanic.Mapper.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import org.mapstruct.Mapper;

/**
 * Mapping 1:1 : les dénormalisations (noms de site/employé, prix/montant des lignes) sont
 * déjà figées sur l'entité par le service.
 */
@Mapper
public interface BonEntreeMapper {
    BonEntreeDto toDto(BonEntree entity);
}
