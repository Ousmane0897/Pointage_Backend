package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class MyUserDetailsService implements UserDetailsService {

    private final LoginRepository loginRepository;
    private final UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String rawEmail) throws UsernameNotFoundException {
        if (rawEmail == null) {
            throw new UsernameNotFoundException("Email vide");
        }

        // Normalisation (important pour éviter les mismatches)
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);

        // Toujours chercher le compte dans la table 'login' (source de vérité pour le mot de passe)
        User loginUser = loginRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Aucun utilisateur trouvé avec l'email : " + email));

        // Chercher le profile Utilisateur (optionnel) pour obtenir rôle / état actif
        Utilisateur profil = utilisateurRepository.findByEmail(email).orElse(null);

        // Si le profil existe et est désactivé -> refuse l'accès
        if (profil != null && !profil.isActive()) {
            throw new DisabledException("Votre compte est désactivé. Veuillez contacter le super admin.");
        }

        // Déterminer le rôle à utiliser (priorise le role du profil si présent)
        String role;
        if (profil != null && profil.getRole() != null) {
            // si RoleAdmin est un enum, utiliser name() ou toString() selon ce qui est stocké
            role = profil.getRole().name();
        } else if (loginUser.getRole() != null) {
            role = loginUser.getRole();
        } else {
            role = "USER"; // fallback si besoin
        }

        return new org.springframework.security.core.userdetails.User(
                loginUser.getEmail(),
                loginUser.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }
}
