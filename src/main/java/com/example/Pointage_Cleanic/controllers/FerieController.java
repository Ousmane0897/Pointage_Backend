package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Ferie;
import com.example.Pointage_Cleanic.repositories.FerieRepository;
import com.example.Pointage_Cleanic.services.FerieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ferie")
@RequiredArgsConstructor
public class FerieController {

    private final FerieService ferieService;
    private final FerieRepository ferieRepository;


    @PostMapping
    public ResponseEntity<Ferie> save(@RequestBody Ferie ferie) {

        Ferie ferie1 = ferieService.save(ferie);
        return ResponseEntity.ok(ferie1);
    }

    @GetMapping
    public ResponseEntity<List<Ferie>> getAll() {
        List<Ferie> feries =  ferieService.getAll();
        return ResponseEntity.ok(feries);
    }

    @PutMapping("/{date}")
    public ResponseEntity<Ferie> updateEmployee(@PathVariable String date, @RequestBody Ferie ferieDetails ) {
        Ferie ferie = ferieService.getById(date);

        if (ferie == null) {
            return ResponseEntity.notFound().build(); // 404
        }

        ferie.setNom(ferieDetails.getNom());
        ferie.setDate(ferieDetails.getDate());


        Ferie ferie1 = ferieService.save(ferie);
        return ResponseEntity.status(HttpStatus.CREATED).body(ferie1);


    }

    @DeleteMapping("/{date}")
    public ResponseEntity<Map<String, Boolean>> Delete(@PathVariable String date) {
        Ferie ferie  = ferieService.getById(date);

        if (ferie == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        ferieRepository.delete(ferie);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }
}
