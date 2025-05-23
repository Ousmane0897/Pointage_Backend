package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.services.EmployeServices;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/employe")
@RequiredArgsConstructor
public class EmployesController {


    private final EmployeServices employeServices;

    private final EmployeRepository employeRepository;


    @PostMapping
    public ResponseEntity<Employe> create(@RequestBody Employe employe) {

        Employe createdEmploye = employeServices.save(employe);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdEmploye);
    }

    @GetMapping
    public ResponseEntity<List<Employe>> getAll() {
        List<Employe> All = employeServices.getAll();
        return ResponseEntity.status(HttpStatus.FOUND).body(All);
    }

    @GetMapping("/{codeSecret}")
    public ResponseEntity<Employe> GetBycodeSecret(@PathVariable Integer codeSecret) {

        Employe employe = employeServices.getBycodeSecret(codeSecret);
        return ResponseEntity.status(HttpStatus.FOUND).body(employe);
    }

    @DeleteMapping("/{codeSecret}")
    public ResponseEntity<Map<String, Boolean>> deleteEmployee(@PathVariable Integer codeSecret) {
        Employe employe = employeRepository.findById(codeSecret)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id:" + codeSecret));

        employeRepository.delete(employe);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{codeSecret}")
    public ResponseEntity<Employe> updateEmployee(@PathVariable Integer codeSecret, @RequestBody Employe employeDetails) {
        Employe employe = employeRepository.findById(codeSecret)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id:" + codeSecret));

        employe.setNom(employeDetails.getNom());
        employe.setPrenom(employeDetails.getPrenom());
        employe.setNumero(employeDetails.getNumero());
        employe.setIntervention(employeDetails.getIntervention());
        employe.setSite(employeDetails.getSite());


        Employe updateEmploye = employeServices.save(employe);
        return ResponseEntity.status(HttpStatus.CREATED).body(updateEmploye);


    }
}

