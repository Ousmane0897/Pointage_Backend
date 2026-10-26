package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.RattachementDossierDto;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.exception.CompteAccesRefuseException;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import com.example.Pointage_Cleanic.util.RattachementEmailUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Contrôle du rattachement des comptes de connexion aux dossiers employés.
 *
 * <p>Le rattachement se fait <b>par l'email seul</b> (le JWT ne porte rien d'autre) : un
 * compte dont l'email ne figure sur aucun dossier ne peut pas déposer de demande de congé.
 * Ce service rend ce lien visible dans la gestion des utilisateurs, pour que la RH sache
 * quelle fiche corriger — il ne crée ni ne modifie jamais rien.
 *
 * <p>La règle de résolution est celle de {@code CongeIdentiteService.employeCourant()}
 * ({@link RattachementEmailUtils#premierDossier}, plus petit id en cas de doublon) : le
 * contrôle doit annoncer exactement le dossier qu'utilisera le dépôt.
 */
@Service
@RequiredArgsConstructor
public class RattachementCompteService {

    private static final String ROLE_SUPERADMIN = "SUPERADMIN";

    private final DossierEmployeRepository dossierEmployeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateursService utilisateursService;
    private final CurrentUserProvider currentUserProvider;

    /** Rattachement d'un email, avant même que le compte existe (formulaire de création). */
    public RattachementDossierDto rattachement(String email) {
        exigerGestionComptes();
        String normalise = RattachementEmailUtils.normaliser(email);
        if (normalise == null) {
            return new RattachementDossierDto();
        }
        return versDto(normalise, RattachementEmailUtils.premierDossier(
                dossierEmployeRepository.findByEmailIgnoreCase(normalise)).orElse(null));
    }

    /**
     * Rattachement de tous les comptes, en une seule lecture des dossiers.
     * ⚠ {@code findByEmailIn} compare à l'identique : il repose sur les emails de dossier
     * normalisés à l'écriture et par {@code DossierEmailNormalisationRunner}.
     */
    public List<RattachementDossierDto> rattachements() {
        exigerGestionComptes();
        List<String> emails = utilisateursService.getAll().stream()
                .map(Utilisateur::getEmail)
                .map(RattachementEmailUtils::normaliser)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (emails.isEmpty()) {
            return List.of();
        }
        Map<String, List<DossierEmploye>> parEmail = dossierEmployeRepository.findByEmailIn(emails)
                .stream()
                .filter(d -> d.getEmail() != null)
                .collect(Collectors.groupingBy(DossierEmploye::getEmail));
        return emails.stream()
                .map(e -> versDto(e, RattachementEmailUtils.premierDossier(parEmail.get(e)).orElse(null)))
                .toList();
    }

    /**
     * Même gate que l'entrée « Gestion des utilisateurs » de la sidebar : le super-admin,
     * ou un compte portant le module {@code admin}. La garde est dans le service — le projet
     * n'active pas {@code @EnableMethodSecurity}, une annotation serait ignorée en silence.
     */
    private void exigerGestionComptes() {
        if (ROLE_SUPERADMIN.equals(currentUserProvider.currentRole())) {
            return;
        }
        String email = currentUserProvider.currentEmail();
        boolean moduleAdmin = email != null && utilisateurRepository.findByEmail(email)
                .map(Utilisateur::getModulesAutorises)
                .map(m -> m.isAdmin())
                .orElse(false);
        if (!moduleAdmin) {
            throw new CompteAccesRefuseException("Gestion des utilisateurs non autorisée pour votre profil.");
        }
    }

    private RattachementDossierDto versDto(String email, DossierEmploye dossier) {
        if (dossier == null) {
            return RattachementDossierDto.builder().email(email).build();
        }
        String prenom = dossier.getPrenom() == null ? "" : dossier.getPrenom().trim();
        String nom = dossier.getNom() == null ? "" : dossier.getNom().trim();
        return RattachementDossierDto.builder()
                .email(email)
                .employeId(dossier.getId())
                .matricule(dossier.getMatricule())
                .nomComplet((prenom + " " + nom).trim())
                .build();
    }
}
