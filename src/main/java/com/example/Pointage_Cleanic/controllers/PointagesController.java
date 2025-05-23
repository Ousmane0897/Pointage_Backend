package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.entities.Pointage;
import com.example.Pointage_Cleanic.repositories.PointageRepository;
import com.example.Pointage_Cleanic.services.PointageServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pointages")
@RequiredArgsConstructor
public class PointagesController {

    private final PointageServices pointageServices;
    private final PointageRepository pointageRepository;

    @PostMapping
    public ResponseEntity<Pointage> create(@RequestBody Pointage pointage) {
        Pointage pointage1 = pointageRepository.save(pointage);
        return ResponseEntity.status(HttpStatus.CREATED).body(pointage1);
    }
    @GetMapping
    public ResponseEntity<List<Pointage>> getAll() {
        List<Pointage> All= pointageRepository.findAll();
        return ResponseEntity.status(HttpStatus.CREATED).body(All);
    }
}
