package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Gab;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.GabRepository;
import com.example.Pointage_Cleanic.services.GabsServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gab")
@RequiredArgsConstructor
public class GabsController {

    private final GabsServices gabsServices ;
    private final GabRepository gabRepository;

    @PostMapping
    public ResponseEntity<Gab> create(@RequestBody Gab gab) {
        Gab gab1 = gabsServices.save(gab);
        return ResponseEntity.status(HttpStatus.CREATED).body(gab1);
    }

    @GetMapping
    public ResponseEntity<List<Gab>> getAll() {
        List<Gab> gabs = gabsServices.getAll();
        return ResponseEntity.status(HttpStatus.FOUND).body(gabs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Gab> getById(@PathVariable String id) {
        Gab gab = gabsServices.getById(id);
        return ResponseEntity.status(HttpStatus.FOUND).body(gab);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteGab(@PathVariable String id) {
        Gab gab  = gabRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gab not found with id:" + id));

        gabRepository.delete(gab);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Gab> updateEmployee(@PathVariable String id, @RequestBody Gab gabDetails) {
        Gab gab  = gabRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id:" + id));

        gab.setSite(gabDetails.getIntervenant());
        gab.setIntervenant(gabDetails.getIntervenant());
        gab.setFrequenceDunettoyage(gabDetails.getFrequenceDunettoyage());

        Gab updateGab = gabsServices.save(gab);
        return ResponseEntity.status(HttpStatus.CREATED).body(updateGab);


    }


}
