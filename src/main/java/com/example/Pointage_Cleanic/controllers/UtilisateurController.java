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
import org.springframework.transaction.annotation.Transactional;
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
        System.out.println("DATA RECU: " + utilisateur);
        String email = utilisateur.getEmail().trim().toLowerCase();

        if (!utilisateurRepository.findAllByEmail(email).isEmpty()
                || !loginRepository.findAllByEmail(email).isEmpty()) {

            throw new EmailAlreadyExistsException(
                    "Cet email existe déjà : " + email
            );
        }

        Utilisateur u = new Utilisateur();
        u.setPrenom(utilisateur.getPrenom());
        u.setNom(utilisateur.getNom());
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode(utilisateur.getPassword()));
        u.setPoste(utilisateur.getPoste());
        u.setRole(utilisateur.getRole());
        u.setModulesAutorises(utilisateur.getModulesAutorises());
        u.setMotifDesactivation(utilisateur.getMotifDesactivation());
        u.setActive(true);

        utilisateurRepository.save(u);

        User login = new User();
        login.setEmail(email);
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

        // Email de référence AVANT modification : sert à retrouver la ligne 'login' à resynchroniser.
        String ancienEmail = utilisateur.getEmail();
        String nouvelEmail = utilisateurDetails.getEmail() != null
                ? utilisateurDetails.getEmail().trim().toLowerCase()
                : ancienEmail;

        // Mot de passe : ne réencoder que si un NOUVEAU mot de passe est fourni.
        // Le formulaire peut renvoyer un champ vide (pas de changement) ou réécho le hash existant
        // (à ne surtout pas réencoder, sinon le compte devient inconnectable).
        String motDePasseRecu = utilisateurDetails.getPassword();
        boolean motDePasseChange = motDePasseRecu != null
                && !motDePasseRecu.isBlank()
                && !motDePasseRecu.equals(utilisateur.getPassword());
        String hashAEnregistrer = motDePasseChange
                ? passwordEncoder.encode(motDePasseRecu)
                : utilisateur.getPassword();

        utilisateur.setPrenom(utilisateurDetails.getPrenom());
        utilisateur.setNom(utilisateurDetails.getNom());
        utilisateur.setEmail(nouvelEmail);
        utilisateur.setPassword(hashAEnregistrer);
        utilisateur.setPoste(utilisateurDetails.getPoste());
        utilisateur.setRole(utilisateurDetails.getRole());
        utilisateur.setModulesAutorises(utilisateurDetails.getModulesAutorises());
        utilisateur.setMotifDesactivation(utilisateurDetails.getMotifDesactivation());
        utilisateur.setActive(utilisateurDetails.isActive());

        Utilisateur utilisateur1 = utilisateursService.save(utilisateur);

        // 🔄 Resynchroniser la collection 'login' (source de vérité du mot de passe à la connexion).
        // Sans ça, le mot de passe / rôle / email réellement utilisés à l'auth restent les anciens
        // et l'utilisateur reçoit un 401 malgré des identifiants "corrects".
        User login = ancienEmail != null ? loginRepository.findByEmail(ancienEmail).orElse(null) : null;
        if (login == null && nouvelEmail != null && !nouvelEmail.equals(ancienEmail)) {
            login = loginRepository.findByEmail(nouvelEmail).orElse(null);
        }
        if (login != null) {
            login.setEmail(nouvelEmail);
            login.setRole(utilisateurDetails.getRole() != null ? utilisateurDetails.getRole().toString() : login.getRole());
            login.setPassword(hashAEnregistrer);
            loginRepository.save(login);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(utilisateur1);
    }
}
