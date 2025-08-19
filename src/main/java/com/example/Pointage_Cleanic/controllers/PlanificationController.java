package com.example.Pointage_Cleanic.controllers;



import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.repositories.PlanificationRepository;
import com.example.Pointage_Cleanic.services.PlanificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/planification")
public class PlanificationController {

    private final PlanificationRepository planificationRepository;
    private final PlanificationService planificationService;


    @PostMapping
    public ResponseEntity<Planification> create(@RequestBody Planification planification) {

        return ResponseEntity.ok(planificationService.save(planification));
    }

    @GetMapping
    public ResponseEntity<List<Planification>> getAll() {

        List<Planification> planifications = planificationService.getAll();

        return ResponseEntity.ok(planifications);
    }

    @GetMapping("/{codeSecret}")
    public ResponseEntity<Planification> getByCodeSecret(@PathVariable String codeSecret) {

        Planification planification = planificationService.getBycodeSecret(codeSecret);

        if (planification == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(planification);
    }

    @DeleteMapping("/{codeSecret}")
    public ResponseEntity<Map<String, Boolean>> cancelPlanification(@PathVariable String codeSecret) {
        Planification planification = planificationService.getBycodeSecret(codeSecret);

        if (planification == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        planificationRepository.delete(planification);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{codeSecret}")
    public ResponseEntity<Planification> updateEmployee(@PathVariable String codeSecret, @RequestBody Planification planificationDetails) {
        Planification planification = planificationService.getBycodeSecret(codeSecret);


        if (planification == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        planification.setNomSite(planificationDetails.getNomSite());
        planification.setDateDebut(planificationDetails.getDateDebut());
        planification.setDateFin(planificationDetails.getDateFin());
        planification.setHeureDebut(planificationDetails.getHeureDebut());
        planification.setHeureFin(planificationDetails.getHeureFin());
        planification.setCommentaires(planificationDetails.getCommentaires());

        Planification planification1 = planificationService.save(planification);
        return ResponseEntity.status(HttpStatus.CREATED).body(planification1);


    }

    @GetMapping("/AVenir/{codeSecret}")
    public ResponseEntity<List<Planification>> planificationsAVenir(@PathVariable String codeSecret) {

        List<Planification> planifications = planificationService.PlanificationsAVenir(codeSecret);

        return ResponseEntity.ok(planifications);
    }

    @GetMapping("/EnCours/{codeSecret}")
    public ResponseEntity<List<Planification>> planificationsEnCours(@PathVariable String codeSecret) {

        List<Planification> planifications = planificationService.PlanificationsEnCours(codeSecret);

        return ResponseEntity.ok(planifications);
    }

    @GetMapping("/Terminees/{codeSecret}")
    public ResponseEntity<List<Planification>> planificationsTerminees(@PathVariable String codeSecret) {

        List<Planification> planifications = planificationService.PlanificationsTerminees(codeSecret);

        return ResponseEntity.ok(planifications);
    }
}
