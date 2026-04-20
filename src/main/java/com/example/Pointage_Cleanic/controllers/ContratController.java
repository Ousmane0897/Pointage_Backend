package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.*;
import com.example.Pointage_Cleanic.services.ContratService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contrats")
@RequiredArgsConstructor
public class ContratController {

    private final ContratService contratService;

    @GetMapping("/alertes-echeance")
    public ResponseEntity<List<AlerteContratDto>> getAlertesEcheance() {
        return ResponseEntity.ok(contratService.getAlertesEcheance());
    }

    @GetMapping
    public ResponseEntity<List<ContratDto>> getAll() {
        return ResponseEntity.ok(contratService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContratDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(contratService.getById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<ContratDto>> getByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(contratService.getByEmployeId(employeId));
    }

    @PostMapping
    public ResponseEntity<ContratDto> create(@RequestBody ContratDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contratService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContratDto> update(@PathVariable String id, @RequestBody ContratDto dto) {
        return ResponseEntity.ok(contratService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        contratService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/renouveler")
    public ResponseEntity<ContratDto> renouveler(
            @PathVariable String id,
            @RequestBody RenouvellerContratRequest request
    ) {
        return ResponseEntity.ok(contratService.renouveler(id, request));
    }

    @PostMapping("/{id}/avenants")
    public ResponseEntity<ContratDto> ajouterAvenant(
            @PathVariable String id,
            @RequestBody AjouterAvenantRequest request
    ) {
        return ResponseEntity.ok(contratService.ajouterAvenant(id, request));
    }

    @PutMapping("/{id}/resilier")
    public ResponseEntity<ContratDto> resilier(@PathVariable String id) {
        return ResponseEntity.ok(contratService.resilier(id));
    }
}
