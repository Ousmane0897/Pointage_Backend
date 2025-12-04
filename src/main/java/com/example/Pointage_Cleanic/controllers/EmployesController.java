package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Agence;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.services.AbsentService;
import com.example.Pointage_Cleanic.services.AgencesServices;
import com.example.Pointage_Cleanic.services.EmployeServices;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.http11.filters.IdentityInputFilter;
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

    private final AgencesServices agencesServices;

    private final AgencesRepository agencesRepository;



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

    @GetMapping("/enDeplacement")
    public ResponseEntity<List<Employe>> employesDeplaces() {

        return ResponseEntity.ok(employeServices.EmployeDeplaces());
    }



    //En Spring Boot, @RequestParam sert à récupérer une valeur passée dans l’URL sous forme de paramètre de requête (après le ?).
    //C’est très pratique pour des recherches ou filtres, surtout quand la valeur peut contenir espaces, accents, caractères spéciaux.

    //Quand utiliser quoi ?
    //
    //@PathVariable 👉 pour un identifiant unique (/employees/123).
    //
    //@RequestParam 👉 pour un paramètre de recherche / filtre (/employeesDansUnSite?site=SGS point e).


    //Pour les afficher dans le formulaire de planification
    @GetMapping("/employeesDansUnSite")
    public ResponseEntity<List<Employe>> getEmployeesDansUnSite(@RequestParam String site) {
        return ResponseEntity.ok(employeServices.EmployeesDansUnSite(site));
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
    public ResponseEntity<Employe> updateEmployeeDeplacement(@PathVariable String codeSecret, @RequestBody Planification planification) {
        Employe employe = employeServices.getBycodeSecret(codeSecret);
        Agence agenceDeDepart = agencesServices.getByNom(planification.getNomSite());
        Agence agenceArrivee = agencesServices.getByNom(planification.getSiteDestination()[0]);



        //Méthode pour la mise à jour de l'heure d'arrivée, heure de départ et changer false -> true pour la variable deplacement,
        //quand un employé est déplacé d'une agence à une autre temporairement.
        if (employe == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        if (agenceDeDepart != null) {

            agenceDeDepart.setDeplacementEmploye(true);
            agencesRepository.save(agenceDeDepart);

        }

        if (agenceArrivee != null) {
            agenceArrivee.setReceptionEmploye(true);
            agencesRepository.save(agenceArrivee);
        }

        String personneRemplacee = planification.getPersonneRemplacee(); // Retourne le prenom et nom de l'agent remplacé
        String[] parts = personneRemplacee.split(" ");
        String prenom = parts[0];
        String nom = parts[1];
        Employe employe1 = employeServices.employeeRemplacee(prenom,nom);
        employe1.setRemplacement(true);
        employeRepository.save(employe1);
        if (planification.isMatin()) {
            employe.setHeureDebut(planification.getHeureDebut());
            employe.setHeureFin(planification.getHeureFin());
            employe.setSiteAvantDeplacement(planification.getNomSite()); // Site dans lequel était l'employé
            employe.setSite(planification.getSiteDestination()); // Le nouveau site de l'employé après déplacement
            employe.setDeplacement(true);
        } else {
            employe.setHeureDebut2(planification.getHeureDebut());
            employe.setHeureFin2(planification.getHeureFin());
            employe.setSiteAvantDeplacement(planification.getNomSite());
            employe.setSite(planification.getSiteDestination());
            employe.setDeplacement(true);
        }


        Employe updateEmploye = employeServices.save(employe);
        return ResponseEntity.status(HttpStatus.CREATED).body(updateEmploye);


    }
}

