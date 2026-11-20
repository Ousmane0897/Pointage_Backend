package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutDossierEmploye;

/**
 * Vue réduite d'un dossier employé, renvoyée aux appelants qui lisent le référentiel des
 * employés depuis un autre module (sélecteurs Terrain, Stock, Chimie, tableau de bord) sans
 * disposer du droit {@code rh.dossierEmploye}.
 *
 * <p>Ne contient <b>aucune</b> donnée de paie (catégorie, RIB, banque, n° IPRES/CSS) ni donnée
 * personnelle (naissance, identité, situation familiale, contacts, adresse, code de pointage).
 */
public record DossierEmployeResumeDto(
        String id,
        String matricule,
        String nom,
        String prenom,
        String poste,
        String departement,
        String siteAffecte,
        StatutDossierEmploye statut
) {

    public static DossierEmployeResumeDto de(DossierEmployeDto d) {
        return new DossierEmployeResumeDto(d.getId(), d.getMatricule(), d.getNom(), d.getPrenom(),
                d.getPoste(), d.getDepartement(), d.getSiteAffecte(), d.getStatut());
    }
}
