package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.AuthRequest;
import com.example.Pointage_Cleanic.Dto.AuthResponse;
import com.example.Pointage_Cleanic.Dto.ChangePasswordRequest;
import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.repositories.*;
import com.example.Pointage_Cleanic.security.JwtUtil;
import com.example.Pointage_Cleanic.services.LoginService;
import com.example.Pointage_Cleanic.services.MyUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/login")
@RequiredArgsConstructor
public class LoginController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final MyUserDetailsService userDetailsService;
    private final LoginService loginService;
    private final LoginRepository loginRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UtilisateurRepository utilisateurRepository;


    @PostMapping
    public ResponseEntity<?> login(@RequestBody AuthRequest authRequest) {

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("Incorrect username or password");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(authRequest.getEmail());


        // First try to find in Utilisateurs
        Optional<Utilisateur> utilisateur = utilisateurRepository.findByEmail(authRequest.getEmail());

        if (utilisateur.isPresent()) {
            Utilisateur utilisateur1 = utilisateur.get();
            String jwt = jwtUtil.generateToken(userDetails,utilisateur1.getPrenom(), utilisateur1.getNom(), utilisateur1.getRole(), utilisateur1.getEmail(), utilisateur1.getPoste(), utilisateur1.isMustChangePassword(), utilisateur1.getModulesAutorises() );
            System.out.println("MODULES BACKEND = " + utilisateur1.getModulesAutorises());
            return ResponseEntity.ok(new AuthResponse(jwt));
        }

//        // Then try to find in SuperAdmin§
          Optional<User> userOpt = loginRepository.findByEmail(authRequest.getEmail());
          if (userOpt.isPresent()) {
              User user = userOpt.get();
              String jwt = jwtUtil.generateToken2(userDetails, user.getRole(), user.getEmail(), user.isMustChangePassword());
              return ResponseEntity.ok(new AuthResponse(jwt));
          }

        // If email not found in either repo
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {

        String email = request.getEmail();
        String oldPass = request.getOldPassword();
        String newPass = request.getNewPassword();
        String confirmPass = request.getConfirmPassword();

        Optional<User> loginOpt = userRepository.findByEmail(email);
        Optional<Utilisateur> utilOpt = utilisateurRepository.findByEmail(email);

        if (loginOpt.isEmpty() && utilOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Utilisateur introuvable"));
        }

        User login = loginOpt.orElse(null);
        Utilisateur util = utilOpt.orElse(null);

        // Vérification ancien mot de passe (selon la table où il existe)
        String storedPassword = login != null ? login.getPassword() : util.getPassword();

        if (!passwordEncoder.matches(oldPass, storedPassword)) {
            return ResponseEntity.badRequest().body(Map.of("message", "L'ancien mot de passe est incorrect."));
        }

        if (!newPass.equals(confirmPass)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Les mots de passe ne correspondent pas."));
        }

        if (newPass.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le mot de passe doit contenir au moins 6 caractères."));
        }

        String encoded = passwordEncoder.encode(newPass);

        // 🔥 Mise à jour synchronisée
        if (login != null) {
            login.setPassword(encoded);
            login.setMustChangePassword(false);
            userRepository.save(login);
        }

        if (util != null) {
            util.setPassword(encoded);
            util.setMustChangePassword(false);
            utilisateurRepository.save(util);
        }

        // Recharger UserDetails
        UserDetails updatedDetails = userDetailsService.loadUserByUsername(email);

        // Choisir token selon type d’utilisateur
        String jwt;
        if (util != null) {
            jwt = jwtUtil.generateToken(
                    updatedDetails,
                    util.getPrenom(),
                    util.getNom(),
                    util.getRole(),
                    util.getEmail(),
                    util.getPoste(),
                    util.isMustChangePassword(),
                    util.getModulesAutorises()
            );
        } else {
            jwt = jwtUtil.generateToken2(
                    updatedDetails,
                    login.getRole(),
                    login.getEmail(),
                    login.isMustChangePassword()
            );
        }

        return ResponseEntity.ok(Map.of(
                "message", "Mot de passe changé avec succès.",
                "token", jwt
        ));
    }





    @GetMapping
    public ResponseEntity<List<User>> get() {
       List<User>  user = loginRepository.findAll();
       return ResponseEntity.ok(user);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteEmployee(@PathVariable String id) {
        User user =  loginService.getById(id);

        if (user == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        loginRepository.delete(user);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }
}
