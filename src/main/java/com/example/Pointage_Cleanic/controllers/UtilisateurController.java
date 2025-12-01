package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Utilisateur;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.exception.EmailAlreadyExistsException;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.SuperAdminRepository;
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

    private final SuperAdminRepository superAdminRepository;
    private final UtilisateursService utilisateursService;
    private final PasswordEncoder passwordEncoder;
    private final LoginRepository loginRepository;

    @PostMapping
    public ResponseEntity<?> save( @RequestBody Utilisateur utilisateur) {

        
        Utilisateur utilisateur1 = new Utilisateur();
        User user = new User();


        if (superAdminRepository.findByEmail(utilisateur.getEmail()).isEmpty()) {

            utilisateur1.setPrenom(utilisateur.getPrenom());
            utilisateur1.setNom(utilisateur.getNom());
            utilisateur1.setEmail(utilisateur.getEmail());
            utilisateur1.setPassword(passwordEncoder.encode(utilisateur.getPassword()));
            utilisateur1.setPoste(utilisateur.getPoste());
            utilisateur1.setRole(utilisateur.getRole());
            utilisateur1.setModulesAutorises(utilisateur.getModulesAutorises());
            utilisateur1.setMotifDesactivation(utilisateur.getMotifDesactivation());
            utilisateur1.setActive(true);
            user.setEmail(utilisateur.getEmail());
            user.setPassword(passwordEncoder.encode(utilisateur.getPassword()));

            loginRepository.save(user);
            superAdminRepository.save(utilisateur1);
        } else {
                throw new EmailAlreadyExistsException("cet Email existe déja:" + utilisateur.getEmail());
        }

        return ResponseEntity.ok(utilisateur1);

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
    public ResponseEntity<Map<String, Boolean>> deleteAdmin(@PathVariable String id) {
        Utilisateur utilisateur = utilisateursService.getByid(id);

        if (utilisateur == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        superAdminRepository.delete(utilisateur);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
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
