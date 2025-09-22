package com.example.Pointage_Cleanic.Mapper;

import com.example.Pointage_Cleanic.Dto.PlanificationDto;
import com.example.Pointage_Cleanic.entities.Planification;

public class PlanificationMapper {

    public static PlanificationDto toDto(Planification entity) {
        if (entity == null) return null;

        PlanificationDto dto = new PlanificationDto();
        dto.setId(entity.getId());
        dto.setPrenomNom(entity.getPrenomNom());
        dto.setStatut(entity.getStatut().toString());
        dto.setMotifAnnulation(entity.getMotifAnnulation()); // <-- mapping

        // mapper les autres champs si besoin
        return dto;
    }

    public static Planification toEntity(PlanificationDto dto) {
        if (dto == null) return null;

        Planification entity = new Planification();
        entity.setId(dto.getId());
        entity.setPrenomNom(dto.getPrenomNom());
        entity.setStatut(Planification.Statut.valueOf(dto.getStatut()));
        entity.setMotifAnnulation(dto.getMotifAnnulation()); // <-- mapping

        // mapper les autres champs si besoin
        return entity;
    }
}


