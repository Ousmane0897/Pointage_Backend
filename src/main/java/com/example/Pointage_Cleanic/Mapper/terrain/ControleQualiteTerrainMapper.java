package com.example.Pointage_Cleanic.Mapper.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.ControleQualiteTerrainDto;
import com.example.Pointage_Cleanic.Dto.terrain.PhotoControleTerrainDto;
import com.example.Pointage_Cleanic.entities.terrain.ControleQualiteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.PhotoControleFichier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.stream.IntStream;

@Mapper(componentModel = "spring")
public interface ControleQualiteTerrainMapper {

    @Mapping(target = "photos", source = "entity", qualifiedByName = "buildPhotos")
    ControleQualiteTerrainDto toDto(ControleQualiteTerrain entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "photos", ignore = true)
    @Mapping(target = "noteGlobale", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ControleQualiteTerrain toEntity(ControleQualiteTerrainDto dto);

    @Named("buildPhotos")
    default List<PhotoControleTerrainDto> buildPhotos(ControleQualiteTerrain entity) {
        if (entity == null || entity.getPhotos() == null || entity.getPhotos().isEmpty()) {
            return List.of();
        }
        final String id = entity.getId();
        return IntStream.range(0, entity.getPhotos().size())
                .mapToObj(i -> {
                    PhotoControleFichier p = entity.getPhotos().get(i);
                    return PhotoControleTerrainDto.builder()
                            .url("/api/terrain/controles-terrain/" + id + "/photos/" + i)
                            .nomFichier(p.getNomFichier())
                            .mimeType(p.getMimeType())
                            .legende(p.getLegende())
                            .build();
                })
                .toList();
    }
}