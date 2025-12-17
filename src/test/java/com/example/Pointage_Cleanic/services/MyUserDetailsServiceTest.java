package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;

import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MyUserDetailsServiceTest {

    @Mock
    private LoginRepository loginRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private MyUserDetailsService myUserDetailsService;


    // ---------------------------------------------------------
    // 1. Email NULL
    // ---------------------------------------------------------
    @Test
    void testLoadUserByUsername_emailNull() {
        assertThrows(UsernameNotFoundException.class, () -> {
            myUserDetailsService.loadUserByUsername(null);
        });
    }


    // ---------------------------------------------------------
    // 2. Aucun loginUser trouvé
    // ---------------------------------------------------------
    @Test
    void testLoadUserByUsername_noLoginUserFound() {

        when(loginRepository.findByEmail("test@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> {
            myUserDetailsService.loadUserByUsername("test@test.com");
        });
    }


    // ---------------------------------------------------------
    // 3. Profil désactivé → DisabledException
    // ---------------------------------------------------------
    @Test
    void testLoadUserByUsername_profileDisabled() {

        User loginUser = new User();
        loginUser.setEmail("test@test.com");
        loginUser.setPassword("1234");
        loginUser.setRole("ADMIN");

        Utilisateur profil = new Utilisateur();
        profil.setEmail("test@test.com");
        profil.setActive(false);

        when(loginRepository.findByEmail("test@test.com")).thenReturn(Optional.of(loginUser));
        when(utilisateurRepository.findByEmail("test@test.com")).thenReturn(Optional.of(profil));

        assertThrows(DisabledException.class, () -> {
            myUserDetailsService.loadUserByUsername("test@test.com");
        });
    }


    // ---------------------------------------------------------
    // 4. Profil trouvé → rôle du profil utilisé
    // ---------------------------------------------------------
    @Test
    void testLoadUserByUsername_profileRoleUsed() {

        User loginUser = new User();
        loginUser.setEmail("test@test.com");
        loginUser.setPassword("pass123");

        Utilisateur profil = new Utilisateur();
        profil.setEmail("test@test.com");
        profil.setActive(true);
        profil.setRole(RoleAdmin.SUPERVISEUR); // ✅ FIX ICI

        when(loginRepository.findByEmail("test@test.com"))
                .thenReturn(Optional.of(loginUser));
        when(utilisateurRepository.findByEmail("test@test.com"))
                .thenReturn(Optional.of(profil));

        UserDetails result =
                myUserDetailsService.loadUserByUsername("test@test.com");

        assertEquals("test@test.com", result.getUsername());
        assertEquals("pass123", result.getPassword());

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPERVISEUR")));
    }



    // ---------------------------------------------------------
    // 5. Profil absent → rôle de loginUser utilisé
    // ---------------------------------------------------------
    @Test
    void testLoadUserByUsername_loginRoleUsed() {

        User loginUser = new User();
        loginUser.setEmail("test@test.com");
        loginUser.setPassword("pass123");
        loginUser.setRole("SUPER");

        when(loginRepository.findByEmail("test@test.com")).thenReturn(Optional.of(loginUser));
        when(utilisateurRepository.findByEmail("test@test.com")).thenReturn(Optional.empty());

        UserDetails result = myUserDetailsService.loadUserByUsername("test@test.com");

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER")));
    }


    // ---------------------------------------------------------
    // 6. Aucun rôle nulle part → fallback USER
    // ---------------------------------------------------------
    @Test
    void testLoadUserByUsername_roleFallback() {

        User loginUser = new User();
        loginUser.setEmail("test@test.com");
        loginUser.setPassword("pass123");
        loginUser.setRole(null); // pas de rôle

        when(loginRepository.findByEmail("test@test.com")).thenReturn(Optional.of(loginUser));
        when(utilisateurRepository.findByEmail("test@test.com")).thenReturn(Optional.empty());

        UserDetails result = myUserDetailsService.loadUserByUsername("test@test.com");

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

}
