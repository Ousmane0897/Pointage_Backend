package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Accès en lecture seule aux dossiers employés (RH) pour le module Stock v2 7.4 :
 * validation + dénormalisation des demandeurs/validateurs, repérage du « Responsable Achats ».
 */
@Service
@RequiredArgsConstructor
public class ReferentielEmployeStockService {

    /** Poste repère du validateur des bons (best-effort, gating réel délégué au frontend). */
    public static final String POSTE_RESPONSABLE_ACHATS = "Responsable Achats";

    private final DossierEmployeRepository dossierEmployeRepository;

    /** Valide qu'un employé existe ; lève IllegalArgumentException (-> 400) sinon. */
    public DossierEmploye valideEtCharge(String employeId) {
        if (employeId == null || employeId.isBlank()) {
            throw new IllegalArgumentException("employeId obligatoire");
        }
        return dossierEmployeRepository.findById(employeId)
                .orElseThrow(() -> new IllegalArgumentException("Employé introuvable : " + employeId));
    }

    /** Nom complet (prénom + nom), ou null si l'id est null/introuvable (dénormalisation tolérante). */
    public String nomComplet(String employeId) {
        if (employeId == null || employeId.isBlank()) {
            return null;
        }
        return dossierEmployeRepository.findById(employeId)
                .map(ReferentielEmployeStockService::nomComplet)
                .orElse(null);
    }

    public static String nomComplet(DossierEmploye e) {
        String prenom = e.getPrenom() == null ? "" : e.getPrenom().trim();
        String nom = e.getNom() == null ? "" : e.getNom().trim();
        String complet = (prenom + " " + nom).trim();
        return complet.isBlank() ? null : complet;
    }

    /** Premier « Responsable Achats » trouvé (par poste, sinon par département « Achats »). */
    public Optional<DossierEmploye> responsableAchats() {
        List<DossierEmploye> parPoste = dossierEmployeRepository.findByPosteIgnoreCase(POSTE_RESPONSABLE_ACHATS);
        if (!parPoste.isEmpty()) {
            return Optional.of(parPoste.get(0));
        }
        return dossierEmployeRepository.findByDepartementIgnoreCase("Achats").stream().findFirst();
    }
}
