package com.example.Pointage_Cleanic.controllers;



import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.models.PointageRequest;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.services.PointageServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/pointages")
@RequiredArgsConstructor
public class PointagesController {

    private final PointageServices pointageServices;
    private final PointageRepository pointageRepository;

    @PostMapping
    public ResponseEntity<?> pointer(@RequestBody PointageRequest request) {

        String code = request.getCodeSecret()
                .replaceAll("[^0-9]", "");


        System.out.println("REQUEST = " + request);
        System.out.println("CODE = " + request.getCodeSecret());
        System.out.println("LONGUEUR = " + request.getCodeSecret().length());
        System.out.println("DEVICE = " + request.getDeviceId());
        System.out.println("LAT = " + request.getLatitude());
        System.out.println("LNG = " + request.getLongitude());


        if (request.getDeviceId() == null || request.getDeviceId().isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Device ID manquant");
        }


        if (!pointageServices.canPoint(request.getDeviceId(), 3)) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Ce téléphone a déjà été utilisé pour un pointage récemment.");
        }

        try {
            Pointage pointage = pointageServices.enregistrerPointage(
                    code,
                    request.getDeviceId(),
                    request.getLatitude(),
                    request.getLongitude()
            );
            return ResponseEntity.ok(pointage);
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(e.getMessage());
        }
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
