package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import com.example.Pointage_Cleanic.entities.GestionModules.SousModules.Rh;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Enrichit le claim {@code modules} à l'émission du JWT pour le circuit de validation
 * des congés.
 *
 * <p>Un manager n'a pas de rôle particulier : c'est le fait d'<b>encadrer au moins un
 * subordonné</b> qui lui ouvre la file de validation. Plutôt que d'exiger qu'on lui coche
 * un droit RH à la main pour chaque nouvelle prise de fonction, le flag
 * {@code rh.congesValidation} est calculé au login depuis l'organigramme.
 *
 * <p>{@code rh.congesMesDemandes} est ouvert à tout compte rattaché à un dossier employé :
 * consulter ses propres demandes ne nécessite aucun privilège.
 *
 * <p>Ce calcul ne modifie <b>jamais</b> ce qui est persisté : il n'agit que sur l'objet
 * sérialisé dans le token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CongeModuleEnricher {

    private final DossierEmployeRepository dossierEmployeRepository;

    /**
     * Renvoie les modules à placer dans le token. Les droits persistés sont conservés :
     * l'enrichissement ne fait qu'ajouter, jamais retirer.
     */
    public ModulesAutorises enrichir(ModulesAutorises modules, String email) {
        if (modules == null || email == null || email.isBlank()) {
            return modules;
        }
        try {
            List<DossierEmploye> dossiers = dossierEmployeRepository.findByEmailIgnoreCase(email.trim());
            if (dossiers.isEmpty()) {
                return modules;   // compte technique, non rattaché à un employé
            }
            String employeId = dossiers.get(0).getId();

            Rh rh = modules.getRh();
            if (rh == null) {
                rh = new Rh();
                modules.setRh(rh);
            }
            if (!rh.isCongesMesDemandes()) {
                rh.setCongesMesDemandes(true);
            }
            if (!rh.isCongesValidation()
                    && dossierEmployeRepository.existsBySuperieurHierarchiqueId(employeId)) {
                rh.setCongesValidation(true);
            }
            return modules;
        } catch (Exception e) {
            // Un incident sur l'organigramme ne doit pas empêcher la connexion.
            log.warn("Enrichissement des droits congés impossible pour {} : {}", email, e.getMessage());
            return modules;
        }
    }
}
