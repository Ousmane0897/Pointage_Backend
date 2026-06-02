package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static com.example.Pointage_Cleanic.services.terrain.TerrainConstantes.DEPARTEMENT_EXPLOITATION;

/**
 * Accès LECTURE SEULE au référentiel RH ({@code dossiers_employes}).
 * <p>
 * Valide qu'un {@code employeId} existe et appartient au département Exploitation,
 * et fournit les valeurs dénormalisées (matricule, nom complet) pour affichage côté front.
 * <strong>N'écrit jamais</strong> dans la collection des employés.
 */
@Service
@RequiredArgsConstructor
public class ReferentielRhService {

    private final DossierEmployeRepository dossierEmployeRepository;

    /** Identité dénormalisée d'un agent. */
    public record AgentRef(String employeId, String matricule, String nomComplet) {
    }

    /**
     * Charge l'employé et vérifie son appartenance au département Exploitation.
     *
     * @throws IllegalArgumentException (→ 400) si absent ou hors département Exploitation.
     */
    public DossierEmploye valideEtCharge(String employeId) {
        if (employeId == null || employeId.isBlank()) {
            throw new IllegalArgumentException("employeId obligatoire");
        }
        DossierEmploye employe = dossierEmployeRepository.findById(employeId)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable : " + employeId));
        if (!DEPARTEMENT_EXPLOITATION.equals(employe.getDepartement())) {
            throw new IllegalArgumentException(
                    "L'employé " + employeId + " n'appartient pas au département " + DEPARTEMENT_EXPLOITATION);
        }
        return employe;
    }

    /** Valide et renvoie l'identité dénormalisée de l'agent. */
    public AgentRef denormalise(String employeId) {
        DossierEmploye e = valideEtCharge(employeId);
        return new AgentRef(e.getId(), e.getMatricule(), nomComplet(e));
    }

    public static String nomComplet(DossierEmploye e) {
        String prenom = e.getPrenom() == null ? "" : e.getPrenom().trim();
        String nom = e.getNom() == null ? "" : e.getNom().trim();
        return (prenom + " " + nom).trim();
    }
}