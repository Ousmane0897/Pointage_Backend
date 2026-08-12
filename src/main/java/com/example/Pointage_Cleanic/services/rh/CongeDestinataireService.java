package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.Enum.rh.NiveauValidationConge;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.rh.DemandeConge;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Résolution des adresses à notifier pour le circuit de validation des congés,
 * partagée par l'e-mail et le WebSocket.
 *
 * <p>Les adresses sont normalisées en minuscules et dédoublonnées ; toute panne d'accès à
 * la base est capturée et loggée — une notification ne doit jamais faire échouer une
 * transition métier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CongeDestinataireService {

    private final DossierEmployeRepository dossierEmployeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final LoginRepository loginRepository;

    /** Adresse du validateur de niveau 1 figé sur la demande. */
    public Optional<String> superieur(DemandeConge demande) {
        String id = demande.getSuperieurHierarchiqueId();
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            return dossierEmployeRepository.findById(id)
                    .map(DossierEmploye::getEmail)
                    .filter(CongeDestinataireService::utilisable)
                    .map(CongeDestinataireService::normaliser);
        } catch (Exception e) {
            log.warn("Résolution du supérieur hiérarchique {} échouée : {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /** Adresse du demandeur (dossier employé). */
    public Optional<String> demandeur(DemandeConge demande) {
        if (demande.getEmployeId() == null) {
            return Optional.empty();
        }
        try {
            return dossierEmployeRepository.findById(demande.getEmployeId())
                    .map(DossierEmploye::getEmail)
                    .filter(CongeDestinataireService::utilisable)
                    .map(CongeDestinataireService::normaliser);
        } catch (Exception e) {
            log.warn("Résolution du demandeur {} échouée : {}", demande.getEmployeId(), e.getMessage());
            return Optional.empty();
        }
    }

    /** Comptes RH actifs — validateurs de niveau 2. */
    public Set<String> rh() {
        try {
            return utilisateurRepository.findByRoleAndActiveTrue(RoleAdmin.RH).stream()
                    .map(Utilisateur::getEmail)
                    .filter(CongeDestinataireService::utilisable)
                    .map(CongeDestinataireService::normaliser)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception e) {
            log.warn("Résolution des comptes RH échouée : {}", e.getMessage());
            return Set.of();
        }
    }

    /** Comptes super-admin — la Direction générale, validateurs de niveau 3. */
    public Set<String> directionGenerale() {
        try {
            return loginRepository.findByRoleIgnoreCase(CongeIdentiteService.ROLE_SUPERADMIN).stream()
                    .map(User::getEmail)
                    .filter(CongeDestinataireService::utilisable)
                    .map(CongeDestinataireService::normaliser)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (Exception e) {
            log.warn("Résolution des comptes super-admin échouée : {}", e.getMessage());
            return Set.of();
        }
    }

    /**
     * Validateurs du niveau attendu par la demande — ceux qui doivent agir maintenant.
     * Pour le niveau 1, c'est le supérieur figé sur la demande, pas un rôle.
     */
    public Set<String> validateursDuNiveau(DemandeConge demande, NiveauValidationConge niveau) {
        if (niveau == null) {
            return Set.of();
        }
        return switch (niveau) {
            case SUPERIEUR -> superieur(demande).map(Set::of).orElseGet(Set::of);
            case RH -> rh();
            case DIRECTION_GENERALE -> directionGenerale();
        };
    }

    /** Fusionne plusieurs jeux d'adresses en conservant l'ordre et sans doublon. */
    @SafeVarargs
    public final Set<String> fusionner(Set<String>... jeux) {
        Set<String> resultat = new LinkedHashSet<>();
        for (Set<String> jeu : jeux) {
            if (jeu != null) {
                resultat.addAll(jeu);
            }
        }
        return resultat;
    }

    public Set<String> optionnel(Optional<String> email) {
        return email.map(Set::of).orElseGet(Set::of);
    }

    /** Validateurs déjà passés, pour les mettre en copie d'un refus. */
    public Set<String> validateursDejaPasses(DemandeConge demande) {
        Set<String> resultat = new LinkedHashSet<>();
        if (demande.getDecisionSuperieur() != null) {
            resultat.addAll(optionnel(superieur(demande)));
        }
        if (demande.getDecisionRh() != null) {
            resultat.addAll(rh());
        }
        if (demande.getDecisionDg() != null) {
            resultat.addAll(directionGenerale());
        }
        return resultat;
    }

    public List<String> enListe(Set<String> emails) {
        return List.copyOf(emails);
    }

    private static boolean utilisable(String email) {
        return email != null && !email.isBlank();
    }

    private static String normaliser(String email) {
        return email.trim().toLowerCase();
    }
}
