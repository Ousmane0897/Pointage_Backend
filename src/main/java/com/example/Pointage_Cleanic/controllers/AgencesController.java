package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.entities.Agence;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.services.AgencesServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agences")
public class AgencesController {


    private final AgencesServices agencesServices ;

    private final AgencesRepository agencesRepository;


    @PostMapping
    public ResponseEntity<Agence> create(@RequestBody Agence agence) {
        Agence createdAgence = agencesServices.save(agence);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdAgence);
    }

    @GetMapping
    public ResponseEntity<List<Agence>> getAll() {
        List<Agence> All = agencesServices.getAll();
        return ResponseEntity.ok(All);
    }


    @GetMapping("/nom/{nom}")
    public ResponseEntity<Agence> GetByNom(@PathVariable String nom) {

        Agence agence = agencesServices.getByNom(nom);

        if (agence == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        return ResponseEntity.ok(agence);
    }

    @DeleteMapping("/{nom}")
    public ResponseEntity<Map<String, Boolean>> deleteAgence(@PathVariable String nom) {
        Agence agence = agencesServices.getByNom(nom);

        if (agence == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        agencesRepository.delete(agence);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }



    @GetMapping("/site/{site}")
    public ResponseEntity<List<Employe>> GetEmployeesBySite(@PathVariable String site) {

        List<Employe> employees = agencesServices.EmployeeParAgence(site);

        return ResponseEntity.ok(employees);
    }

    @GetMapping("/sites")
    public ResponseEntity<List<String>> getAllSites() {

        List<String> sites = agencesServices.getAllSiteNames();

        return ResponseEntity.ok(sites);
    }

    @GetMapping("/getNumberofEmployeesInOneAgence/{nomAgence}")
    public ResponseEntity<Integer> getNumberofEmployeesInOneAgence(@PathVariable String nomAgence) {

        return ResponseEntity.ok(agencesServices.getNumberofEmployeesInOneAgence(nomAgence));
    }

    @GetMapping("/MaxNumberOfEmployeesInOneAgence/{nomAgence}")
    public ResponseEntity<Integer> getMaxNumberOfEmployeesInOneAgence(@PathVariable String nomAgence) {

        return ResponseEntity.ok(agencesServices.getMaxNumberOfEmployeesInOneAgence(nomAgence));
    }

    @GetMapping(value = "/{nomAgence}", produces = "text/plain")
    public ResponseEntity<String> getJoursOuvertureByNom(@PathVariable String nomAgence) {
        String joursOuverture = agencesServices.getJoursOuvertureByNom(nomAgence);
        if (joursOuverture == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(joursOuverture);
    } // Avec produces = "text/plain" au lieu d'envoyer un objet '{ "joursOuverture": "Lundi-Vendredi" }, spring envoi seulement 'Lundi-Vendredi'


    @PutMapping("/{nom}")
    public ResponseEntity<Agence> updateEmployee(@PathVariable String nom, @RequestBody Agence agenceDetails) {

        Agence agence = agencesServices.getByNom(nom);

        if (agence == null) {
            return ResponseEntity.notFound().build(); // 404
        }

       agence.setNom(agenceDetails.getNom());
       agence.setAdresse(agenceDetails.getAdresse());
       agence.setHeuresTravail(agence.getHeuresTravail());
       agence.setJoursOuverture(agenceDetails.getJoursOuverture());
       agence.setNombreAgentsMaximum(agenceDetails.getNombreAgentsMaximum());


        Agence updatedAgence = agencesServices.save(agence);
        return ResponseEntity.status(HttpStatus.CREATED).body(updatedAgence);


    }
}
