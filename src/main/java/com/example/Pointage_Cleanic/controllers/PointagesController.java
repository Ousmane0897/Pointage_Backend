package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.models.PointageRequest;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.services.PointageServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/pointages")
@RequiredArgsConstructor
public class PointagesController {

    private final PointageServices pointageServices;
    private final PointageRepository pointageRepository;

    @PostMapping
    public ResponseEntity<?> pointer(@RequestBody PointageRequest request) {
        if (!pointageServices.canPoint(request.getDeviceId(), 2)) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Ce téléphone a déjà été utilisé pour un pointage récemment.");
        }

        Pointage pointage = pointageServices.enregistrerPointage(request.getCodeSecret(), request.getDeviceId());
        return ResponseEntity.ok(pointage);
    }

    @GetMapping
    public ResponseEntity<List<Pointage>> getAll() {
        List<Pointage> All= pointageRepository.findAll();
        return ResponseEntity.status(HttpStatus.CREATED).body(All);
    }

    @GetMapping("/{codeSecret}")
    public ResponseEntity<Pointage> GetBycodeSecret(@PathVariable String codeSecret) {

        Pointage pointage = pointageServices.getPointageBycodeSecret(codeSecret);

        if (pointage == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        return ResponseEntity.ok(pointage);
    }


}
