package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Fournit l'identité de l'utilisateur courant (issu du JWT) pour renseigner
 * automatiquement les champs {@code resoluPar*}, {@code controleur*}, {@code updatedBy}, etc.
 * <p>
 * Le JWT porte l'email (subject). On résout l'id et le nom complet via la collection
 * {@code utilisateur}. À défaut (utilisateur non trouvé / contexte non authentifié),
 * l'email est utilisé comme identifiant et comme nom de repli.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UtilisateurRepository utilisateurRepository;

    private Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /** Email (subject) de l'utilisateur courant, ou {@code null}. */
    public String currentEmail() {
        Authentication a = auth();
        return (a == null || a.getName() == null) ? null : a.getName();
    }

    /** Identifiant de l'utilisateur courant : id Utilisateur si trouvé, sinon email. */
    public String currentUserId() {
        String email = currentEmail();
        if (email == null) {
            return null;
        }
        return utilisateurRepository.findByEmail(email)
                .map(Utilisateur::getId)
                .orElse(email);
    }

    /** Nom complet de l'utilisateur courant (prénom + nom), sinon email. */
    public String currentUserNom() {
        String email = currentEmail();
        if (email == null) {
            return null;
        }
        return utilisateurRepository.findByEmail(email)
                .map(u -> {
                    String prenom = u.getPrenom() == null ? "" : u.getPrenom().trim();
                    String nom = u.getNom() == null ? "" : u.getNom().trim();
                    String complet = (prenom + " " + nom).trim();
                    return complet.isBlank() ? email : complet;
                })
                .orElse(email);
    }
}