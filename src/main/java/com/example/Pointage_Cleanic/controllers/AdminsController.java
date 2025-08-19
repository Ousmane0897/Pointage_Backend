package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Admins;
import com.example.Pointage_Cleanic.entities.User;
import com.example.Pointage_Cleanic.exception.EmailAlreadyExistsException;
import com.example.Pointage_Cleanic.repositories.LoginRepository;
import com.example.Pointage_Cleanic.repositories.SuperAdminRepository;
import com.example.Pointage_Cleanic.services.AdminsService;
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
public class AdminsController {

    private final SuperAdminRepository superAdminRepository;
    private final AdminsService adminsService;
    private final PasswordEncoder passwordEncoder;
    private final LoginRepository loginRepository;

    @PostMapping
    public ResponseEntity<?> save( @RequestBody Admins admins) {

        
        Admins admins1 = new Admins();
        User user = new User();

        if (superAdminRepository.findByEmail(admins.getEmail()).isEmpty()) {

            admins1.setIdentifiant(admins.getIdentifiant());
            admins1.setPrenom(admins.getPrenom());
            admins1.setNom(admins.getNom());
            admins1.setEmail(admins.getEmail());
            admins1.setPassword(passwordEncoder.encode(admins.getPassword()));
            admins1.setPoste(admins.getPoste());
            admins1.setRole(admins.getRole());
            admins1.setMotifDesactivation(admins.getMotifDesactivation());
            admins1.setActive(true);
            user.setEmail(admins.getEmail());
            user.setPassword(passwordEncoder.encode(admins.getPassword()));

            loginRepository.save(user);
            superAdminRepository.save(admins1);
        } else {
                throw new EmailAlreadyExistsException("cet Email existe déja:" + admins.getEmail());
        }

        return ResponseEntity.ok(admins1);

    }

    @GetMapping
    public ResponseEntity<List<Admins>> getAll() {

        return ResponseEntity.ok(adminsService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Admins> getById(@PathVariable String id) {
        Admins admins = adminsService.getByid(id);

        if (admins == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(admins);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteAdmin(@PathVariable String id) {
        Admins admins = adminsService.getByid(id);

        if (admins == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        superAdminRepository.delete(admins);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Admins> updateAdmin(@PathVariable String id, @RequestBody Admins adminsDetails) {
        Admins admins = adminsService.getByid(id);

        if (admins == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        admins.setPrenom(adminsDetails.getPrenom());
        admins.setNom(adminsDetails.getNom());
        admins.setEmail(adminsDetails.getEmail());
        admins.setPassword(adminsDetails.getPassword());
        admins.setPoste(adminsDetails.getPoste());
        admins.setRole(admins.getRole());
        admins.setMotifDesactivation(adminsDetails.getMotifDesactivation());
        admins.setActive(adminsDetails.isActive());

        Admins admins1  = adminsService.save(admins);
        return ResponseEntity.status(HttpStatus.CREATED).body(admins1);


    }
}
