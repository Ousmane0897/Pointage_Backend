package com.example.Pointage_Cleanic.Mapper.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.FicheInterventionDto;
import com.example.Pointage_Cleanic.Dto.terrain.PhotoInterventionDto;
import com.example.Pointage_Cleanic.entities.terrain.FicheIntervention;
import com.example.Pointage_Cleanic.entities.terrain.PhotoInterventionFichier;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;
import java.util.stream.IntStream;

@Mapper(componentModel = "spring")
public interface FicheInterventionMapper {

    @Mapping(target = "photos", source = "entity", qualifiedByName = "buildPhotos")
    @Mapping(target = "photosMeta", ignore = true)
    @Mapping(target = "photosConservees", ignore = true)
    FicheInterventionDto toDto(FicheIntervention entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "numero", ignore = true)
    @Mapping(target = "duree", ignore = true)
    @Mapping(target = "photos", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    FicheIntervention toEntity(FicheInterventionDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "numero", ignore = true)
    @Mapping(target = "duree", ignore = true)
    @Mapping(target = "photos", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromDto(FicheInterventionDto dto, @MappingTarget FicheIntervention entity);

    @Named("buildPhotos")
    default List<PhotoInterventionDto> buildPhotos(FicheIntervention entity) {
        if (entity == null || entity.getPhotos() == null || entity.getPhotos().isEmpty()) {
            return List.of();
        }
        final String id = entity.getId();
        return IntStream.range(0, entity.getPhotos().size())
                .mapToObj(i -> {
                    PhotoInterventionFichier p = entity.getPhotos().get(i);
                    return PhotoInterventionDto.builder()
                            .url("/api/terrain/interventions/" + id + "/photos/" + i)
                            .nomFichier(p.getNomFichier())
                            .mimeType(p.getMimeType())
                            .tailleOctets(p.getTailleOctets())
                            .moment(p.getMoment())
                            .legende(p.getLegende())
                            .build();
                })
                .toList();
    }
}