package com.example.Pointage_Cleanic.Mapper.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ControleQualiteDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.PhotoControleDto;
import com.example.Pointage_Cleanic.entities.productionchimie.ControleQualite;
import com.example.Pointage_Cleanic.entities.productionchimie.PhotoControle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.stream.IntStream;

@Mapper(componentModel = "spring")
public interface ControleQualiteMapper {

    @Mapping(target = "photos", source = "entity", qualifiedByName = "buildPhotoDtos")
    ControleQualiteDto toDto(ControleQualite entity);

    @Mapping(target = "photos", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ControleQualite toEntity(ControleQualiteDto dto);

    @Named("buildPhotoDtos")
    default List<PhotoControleDto> buildPhotoDtos(ControleQualite entity) {
        if (entity == null || entity.getPhotos() == null || entity.getPhotos().isEmpty()) {
            return List.of();
        }
        final String idCtrl = entity.getId();
        return IntStream.range(0, entity.getPhotos().size())
                .mapToObj(i -> {
                    PhotoControle p = entity.getPhotos().get(i);
                    return PhotoControleDto.builder()
                            .url("/api/production-chimie/controle-qualite/controles/" + idCtrl + "/photos/" + i)
                            .nomFichier(p.getNomFichier())
                            .mimeType(p.getMimeType())
                            .build();
                })
                .toList();
    }
}
