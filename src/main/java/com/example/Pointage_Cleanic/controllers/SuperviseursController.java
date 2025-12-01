package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.entities.Superviseur;
import com.example.Pointage_Cleanic.services.SuperviseursServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/superviseurs")
@RequiredArgsConstructor
public class SuperviseursController {

    private final SuperviseursServices services;


    @PostMapping
    public ResponseEntity<Superviseur> create (@RequestBody Superviseur superviseur)  {

        return ResponseEntity.ok(services.save(superviseur));
    }

    @GetMapping
    public ResponseEntity<List<Superviseur>> getAll() {

        return ResponseEntity.ok(services.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Superviseur> getById(@PathVariable String id) {

        Superviseur superviseur = services.getById(id);

        if (superviseur == null) {

            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(superviseur);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {

        services.delete(id);
    }

    @GetMapping("/update/{id}")
    public ResponseEntity<Superviseur> update(@PathVariable String id, @RequestBody Superviseur superviseur) {

        return ResponseEntity.ok(services.update(id,superviseur));
    }
}
