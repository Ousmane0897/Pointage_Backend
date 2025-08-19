package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.services.EmployeServices;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/employe")
@RequiredArgsConstructor
public class EmployesController {


    private final EmployeServices employeServices;

    private final EmployeRepository employeRepository;


    @PostMapping
    public ResponseEntity<Employe> create(@RequestBody @Valid Employe employe) {
        System.out.println("Reçu en backend : employeCreePar = " + employe.getEmployeCreePar());
        LocalDateTime maintenant = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String formatte = maintenant.format(formatter);

        employe.setDateEtHeureCreation(formatte);

        Employe createdEmploye = employeServices.save(employe);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEmploye);
    }

    @GetMapping
    public ResponseEntity<List<Employe>> getAll() {
        List<Employe> All = employeServices.getAll();
        return ResponseEntity.ok(All);
    }

    @GetMapping("/Cheffes")
    public ResponseEntity<List<Employe>> GetCheffesEquipe() {

        List<Employe> cheffes = employeServices.CheffeEquipe();

        return ResponseEntity.ok(cheffes);
    }

    @GetMapping("/{codeSecret}")
    public ResponseEntity<Employe> GetBycodeSecret(@PathVariable String codeSecret) {

        Employe employe = employeServices.getBycodeSecret(codeSecret);

        if (employe == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        return ResponseEntity.ok(employe);
    }

    @DeleteMapping("/{codeSecret}")
    public ResponseEntity<Map<String, Boolean>> deleteEmployee(@PathVariable String codeSecret) {
        Employe employe = employeServices.getBycodeSecret(codeSecret);

        if (employe == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        employeRepository.delete(employe);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{codeSecret}")
    public ResponseEntity<Employe> updateEmployee(@PathVariable String codeSecret, @RequestBody Employe employeDetails) {
        Employe employe = employeServices.getBycodeSecret(codeSecret);

        if (employe == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        employe.setNom(employeDetails.getNom());
        employe.setPrenom(employeDetails.getPrenom());
        employe.setNumero(employeDetails.getNumero());
        employe.setHeureDebut(employeDetails.getHeureDebut());
        employe.setHeureFin(employeDetails.getHeureFin());
        employe.setHeureDebut2(employeDetails.getHeureDebut2());
        employe.setHeureFin2(employeDetails.getHeureFin2());
        employe.setJoursDeTravail(employeDetails.getJoursDeTravail());
        employe.setJoursDeTravail2(employeDetails.getJoursDeTravail2());
        employe.setIntervention(employeDetails.getIntervention());
        employe.setStatut(employeDetails.getStatut());
        employe.setEmployeCreePar(employe.getEmployeCreePar());
        employe.setSite(employeDetails.getSite());


        Employe updateEmploye = employeServices.save(employe);
        return ResponseEntity.status(HttpStatus.CREATED).body(updateEmploye);


    }

    @PutMapping("deplacement/{codeSecret}")
    public ResponseEntity<Employe> updateEmployeeDeplacement(@PathVariable String codeSecret, @RequestBody Employe employeDetails) {
        Employe employe = employeServices.getBycodeSecret(codeSecret);
        //Méthode pour la mise à jour de l'heure d'arrivée, heure de départ et changer false -> true pour la variable deplacement,
        //quand un employé est déplacé d'une agence à une autre temporairement
        if (employe == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        employe.setHeureDebut(employeDetails.getHeureDebut());
        employe.setHeureFin(employeDetails.getHeureFin());
        employe.setHeureDebut2(employeDetails.getHeureDebut2());
        employe.setHeureFin2(employeDetails.getHeureFin2());
        employe.setDeplacement(true);

        Employe updateEmploye = employeServices.save(employe);
        return ResponseEntity.status(HttpStatus.CREATED).body(updateEmploye);


    }
}

