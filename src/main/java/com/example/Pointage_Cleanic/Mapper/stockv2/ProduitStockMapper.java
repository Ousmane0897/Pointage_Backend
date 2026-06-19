package com.example.Pointage_Cleanic.Mapper.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ProduitDto;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper
public interface ProduitStockMapper {

    @Mapping(target = "categorieLibelle", ignore = true)
    @Mapping(target = "quantiteTotale", ignore = true)
    @Mapping(target = "photoUrl", source = "entity", qualifiedByName = "photoUrl")
    @Mapping(target = "ficheTechniqueUrl", source = "entity", qualifiedByName = "ficheUrl")
    ProduitDto toDto(ProduitStock entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "photoNom", ignore = true)
    @Mapping(target = "photoMimeType", ignore = true)
    @Mapping(target = "ficheTechnique", ignore = true)
    @Mapping(target = "ficheTechniqueNom", ignore = true)
    @Mapping(target = "ficheTechniqueMimeType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    // 7.6 : non éditables via le formulaire produit 7.3 ; seuls les PATCH dédiés les écrivent.
    @Mapping(target = "methodeValorisation", ignore = true)
    @Mapping(target = "prixVente", ignore = true)
    ProduitStock toEntity(ProduitDto dto);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "photo", ignore = true)
    @Mapping(target = "photoNom", ignore = true)
    @Mapping(target = "photoMimeType", ignore = true)
    @Mapping(target = "ficheTechnique", ignore = true)
    @Mapping(target = "ficheTechniqueNom", ignore = true)
    @Mapping(target = "ficheTechniqueMimeType", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    // 7.6 : non éditables via le formulaire produit 7.3 ; seuls les PATCH dédiés les écrivent.
    @Mapping(target = "methodeValorisation", ignore = true)
    @Mapping(target = "prixVente", ignore = true)
    void updateEntityFromDto(ProduitDto dto, @MappingTarget ProduitStock entity);

    @Named("photoUrl")
    default String photoUrl(ProduitStock entity) {
        if (entity == null || entity.getPhoto() == null || entity.getId() == null) {
            return null;
        }
        return "/api/stock/produits/" + entity.getId() + "/photo";
    }

    @Named("ficheUrl")
    default String ficheTechniqueUrl(ProduitStock entity) {
        if (entity == null || entity.getFicheTechnique() == null || entity.getId() == null) {
            return null;
        }
        return "/api/stock/produits/" + entity.getId() + "/fiche-technique";
    }
}
