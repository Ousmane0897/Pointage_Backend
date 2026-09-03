package com.example.Pointage_Cleanic.Dto.rh;

import lombok.*;

/**
 * Employé au nom duquel l'appelant peut déposer une demande de congé.
 *
 * <p>Alimente le champ « Employé » du formulaire de demande. La liste est
 * entièrement bornée serveur ({@code CongeIdentiteService.perimetreDepot()}) : le
 * front ne filtre rien et n'a jamais à deviner la hiérarchie.
 *
 * <p>⚠ {@code superieurHierarchiqueId} / {@code superieurHierarchiqueNom} ne sont pas
 * décoratifs : le formulaire s'en sert pour annoncer le validateur de niveau 1 et
 * prévenir que ce niveau sera sauté.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeSelectionnableDto {

    private String id;
    private String matricule;
    private String nom;
    private String prenom;
    private String departement;
    private String superieurHierarchiqueId;
    private String superieurHierarchiqueNom;

    /** {@code true} pour l'appelant lui-même — trié en tête de liste. */
    private boolean estMoi;
}
