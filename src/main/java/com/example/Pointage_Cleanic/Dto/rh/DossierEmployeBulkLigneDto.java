package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO pour une ligne d'import bulk de dossier employé.
 * <p>
 * Étend {@link DossierEmployeDto} et ajoute {@code superieurHierarchiqueMatricule} :
 * pendant l'import, le supérieur peut être référencé par son matricule
 * (plutôt que par son ObjectId Mongo qui n'existe pas encore si le supérieur
 * est lui-même importé dans le même batch). La résolution matricule → id
 * est faite par le service en deux passes.
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DossierEmployeBulkLigneDto extends DossierEmployeDto {

    private String superieurHierarchiqueMatricule;
}