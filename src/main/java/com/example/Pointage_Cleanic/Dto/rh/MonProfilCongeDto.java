package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

/**
 * Identité métier de l'utilisateur connecté, résolue depuis l'e-mail du JWT.
 *
 * <p>Le JWT ne porte ni {@code id} ni {@code employeId} : c'est le seul moyen pour le front
 * de savoir « qui suis-je » et « de qui suis-je le supérieur ».
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonProfilCongeDto {

    /** null si le compte n'est rattaché à aucun dossier employé. */
    private String employeId;

    private String matricule;
    private String nom;
    private String prenom;
    private String email;
    private String departement;

    private String superieurHierarchiqueId;
    private String superieurHierarchiqueNom;

    /** Niveaux que l'appelant peut valider (subordonnés + rôles). */
    private List<NiveauValidationConge> niveauxValidables;

    /** Demandes en attente de son action, tous niveaux confondus. */
    private Long nbDemandesAValider;
}
