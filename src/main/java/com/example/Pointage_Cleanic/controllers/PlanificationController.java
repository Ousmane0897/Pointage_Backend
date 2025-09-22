package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.Dto.CancelRequestDto;
import com.example.Pointage_Cleanic.Dto.PlanificationDto;
import com.example.Pointage_Cleanic.Dto.ValidationRequestDto;
import com.example.Pointage_Cleanic.Mapper.PlanificationMapper;
import com.example.Pointage_Cleanic.entities.Planification;
import com.example.Pointage_Cleanic.repositories.PlanificationRepository;
import com.example.Pointage_Cleanic.services.PlanificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/planification")
@RequiredArgsConstructor
public class PlanificationController {

    private final PlanificationService service;
    private final PlanificationRepository repository;

    @PostMapping
    public ResponseEntity<PlanificationDto> create(@RequestBody Planification plan) {
        return ResponseEntity.ok(service.createPlanification(plan));
    }

    @GetMapping
    public List<PlanificationDto> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlanificationDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }


    /**
     * Annule une planification en fonction de son ID.
     * id L'identifiant de la planification à annuler
     */
    @PostMapping("/cancel")
    public ResponseEntity<PlanificationDto> cancelPlanification(@RequestBody CancelRequestDto requestDto) {
        boolean cancelled = service.cancelPlanification(requestDto.getId(), requestDto.getMotif());

        if (!cancelled) {
            return ResponseEntity.notFound().build();
        }

        Planification updated = repository.findById(requestDto.getId()).get();
        PlanificationDto dto = PlanificationDto.fromEntity(updated);
        return ResponseEntity.ok(dto);
    }



    @PutMapping("/{id}")
    public ResponseEntity<Optional<PlanificationDto>> update(@PathVariable String id, @RequestBody Planification plan) {
        return ResponseEntity.ok(service.updatePlanification(id,plan));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }

    /**
     * Ici Principal fait référence à l’interface Java java.security.Principal.
     * C’est ce que Spring Security met à disposition dans tes controllers REST pour savoir qui est l’utilisateur connecté.
     */
    @PostMapping("/demander")
    public ResponseEntity<PlanificationDto> demanderAnnulation(@RequestBody CancelRequestDto dto, Principal principal) {
        // récupère le username depuis le token JWT si principal.getName() n'est pas null.
        String requestedBy = principal != null ? principal.getName() : dto.getRequestedBy();
        PlanificationDto updated = service.demanderAnnulation(dto.getId(), dto.getMotif(), requestedBy);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/valider")
    public ResponseEntity<PlanificationDto> validerAnnulation(@RequestBody ValidationRequestDto dto, Principal principal) {
        String validatedBy = principal != null ? principal.getName() : dto.getValidatedBy();
        PlanificationDto updated = service.validerAnnulation(dto.getId(), dto.isAccepted(), validatedBy);
        return ResponseEntity.ok(updated);
    }
}

/*
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
**/
