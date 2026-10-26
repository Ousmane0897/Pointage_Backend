package com.example.Pointage_Cleanic.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rattachement d'un compte de connexion à un dossier employé, par l'email.
 * {@code employeId} est {@code null} quand aucun dossier ne porte cet email :
 * le compte ne peut alors pas déposer de demande de congé.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RattachementDossierDto {

    /** Email normalisé du compte. */
    private String email;

    private String employeId;

    private String matricule;

    /** Prénom + nom du dossier rattaché. */
    private String nomComplet;
}
