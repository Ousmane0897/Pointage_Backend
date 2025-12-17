package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.exception.EmailAlreadyExistsException;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.UtilisateurRepository;
import com.example.Pointage_Cleanic.services.UtilisateursService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/superadmin")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurRepository utilisateurRepository;
    private final UtilisateursService utilisateursService;
    private final PasswordEncoder passwordEncoder;
    private final LoginRepository loginRepository;

    @PostMapping
    public ResponseEntity<?> save(@RequestBody Utilisateur utilisateur) {

        // 🛑 Vérifier dans les deux collections
        if (utilisateurRepository.findByEmail(utilisateur.getEmail()).isPresent()
                || loginRepository.findByEmail(utilisateur.getEmail()).isPresent()) {

            throw new EmailAlreadyExistsException(
                    "Cet email existe déjà : " + utilisateur.getEmail()
            );
        }

        // ✔ Créer un compte utilisateur complet
        Utilisateur u = new Utilisateur();
        u.setPrenom(utilisateur.getPrenom());
        u.setNom(utilisateur.getNom());
        u.setEmail(utilisateur.getEmail());
        u.setPassword(passwordEncoder.encode(utilisateur.getPassword()));
        u.setPoste(utilisateur.getPoste());
        u.setRole(utilisateur.getRole());
        u.setModulesAutorises(utilisateur.getModulesAutorises());
        u.setMotifDesactivation(utilisateur.getMotifDesactivation());
        u.setActive(true);

        utilisateurRepository.save(u);

        // ✔ Créer un compte login pour l’authentification
        User login = new User();
        login.setEmail(utilisateur.getEmail());
        login.setPassword(passwordEncoder.encode(utilisateur.getPassword()));
        login.setRole(utilisateur.getRole().toString());
        login.setMustChangePassword(true);

        loginRepository.save(login);

        return ResponseEntity.ok(u);
    }


    @GetMapping
    public ResponseEntity<List<Utilisateur>> getAll() {

        return ResponseEntity.ok(utilisateursService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Utilisateur> getById(@PathVariable String id) {
        Utilisateur utilisateur = utilisateursService.getByid(id);

        if (utilisateur == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(utilisateur);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUtilisateur(@PathVariable String id) {

        Utilisateur u = utilisateurRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        // 🔥 Supprimer le User associé dans login
        loginRepository.deleteByEmail(u.getEmail());

        // 🔥 Puis supprimer l’utilisateur
        utilisateurRepository.delete(u);

        return ResponseEntity.ok(Map.of("deleted", true));
    }


    @PutMapping("/{id}")
    public ResponseEntity<Utilisateur> updateAdmin(@PathVariable String id, @RequestBody Utilisateur utilisateurDetails) {
        Utilisateur utilisateur = utilisateursService.getByid(id);

        if (utilisateur == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        utilisateur.setPrenom(utilisateurDetails.getPrenom());
        utilisateur.setNom(utilisateurDetails.getNom());
        utilisateur.setEmail(utilisateurDetails.getEmail());
        utilisateur.setPassword(utilisateurDetails.getPassword());
        utilisateur.setPoste(utilisateurDetails.getPoste());
        utilisateur.setRole(utilisateur.getRole());
        utilisateur.setMotifDesactivation(utilisateurDetails.getMotifDesactivation());
        utilisateur.setActive(utilisateurDetails.isActive());

        Utilisateur utilisateur1 = utilisateursService.save(utilisateur);
        return ResponseEntity.status(HttpStatus.CREATED).body(utilisateur1);


    }
}
