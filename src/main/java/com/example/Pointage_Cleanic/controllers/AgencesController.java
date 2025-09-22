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


    @GetMapping("/nom")
    public ResponseEntity<Agence> GetByNom(@RequestParam String nomAgence) {

        Agence agence = agencesServices.getByNom(nomAgence);

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



    @GetMapping("/site")
    public ResponseEntity<List<Employe>> GetEmployeesBySite(@RequestParam String nomAgence) {

        List<Employe> employees = agencesServices.EmployeeParAgence(nomAgence);

        return ResponseEntity.ok(employees);
    }

    @GetMapping("/sites")
    public ResponseEntity<List<String>> getAllSites() {

        List<String> sites = agencesServices.getAllSiteNames();

        return ResponseEntity.ok(sites);
    }

    //En Spring Boot, @RequestParam sert à récupérer une valeur passée dans l’URL sous forme de paramètre de requête (après le ?).
    //C’est très pratique pour des recherches ou filtres, surtout quand la valeur peut contenir espaces, accents, caractères spéciaux.

    //Quand utiliser quoi ?
    //
    //@PathVariable 👉 pour un identifiant unique (/employees/123).
    //
    //@RequestParam 👉 pour un paramètre de recherche / filtre (/employeesDansUnSite?site=SGS point e).

    @GetMapping("/getNumberofEmployeesInOneAgence")
    public ResponseEntity<Integer> getNumberofEmployeesInOneAgence(@RequestParam String nomAgence) {

        int max = agencesServices.getNumberofEmployeesInOneAgence(nomAgence.trim());
        return ResponseEntity.ok(max);
    }


    @GetMapping("/getEmployeeDeplacee")
    public ResponseEntity<Employe> getEmployeeDeplacee(@RequestParam String nomAgence) {
        return ResponseEntity.ok(agencesServices.EmployeeDeplacee(nomAgence));
    }

    @GetMapping("/getEmployeeRemplacee")
    public ResponseEntity<Employe> getEmployeeRemplacee(@RequestParam String nomAgence) {
        return ResponseEntity.ok(agencesServices.EmployeeRemplacee(nomAgence));
    }

    @GetMapping("/MaxNumberOfEmployeesInOneAgence")
    public ResponseEntity<Integer> getMaxNumberOfEmployeesInOneAgence(@RequestParam String nomAgence) {

        int max = agencesServices.getMaxNumberOfEmployeesInOneAgence(nomAgence.trim());
        return ResponseEntity.ok(max);
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
