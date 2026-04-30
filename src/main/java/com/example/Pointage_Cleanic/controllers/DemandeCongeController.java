package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.SoldeCongeDto;
import com.example.Pointage_Cleanic.services.DemandeCongeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/conges")
@RequiredArgsConstructor
public class DemandeCongeController {

    private final DemandeCongeService demandeCongeService;

    @GetMapping("/solde/{employeId}")
    public ResponseEntity<SoldeCongeDto> getSolde(@PathVariable String employeId) {
        return ResponseEntity.ok(demandeCongeService.getSolde(employeId));
    }

    @GetMapping
    public ResponseEntity<List<DemandeCongeDto>> getAll() {
        return ResponseEntity.ok(demandeCongeService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DemandeCongeDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(demandeCongeService.getById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<DemandeCongeDto>> getByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(demandeCongeService.getByEmployeId(employeId));
    }

    @PostMapping
    public ResponseEntity<DemandeCongeDto> create(@RequestBody DemandeCongeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(demandeCongeService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DemandeCongeDto> update(@PathVariable String id, @RequestBody DemandeCongeDto dto) {
        return ResponseEntity.ok(demandeCongeService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        demandeCongeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/approuver")
    public ResponseEntity<DemandeCongeDto> approuver(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String decideurId = body != null ? body.getOrDefault("decideurId", null) : null;
        String decideurNom = body != null ? body.getOrDefault("decideurNom", null) : null;
        String commentaire = body != null ? body.getOrDefault("commentaire", null) : null;
        return ResponseEntity.ok(demandeCongeService.approuver(id, decideurId, decideurNom, commentaire));
    }

    @PutMapping("/{id}/refuser")
    public ResponseEntity<DemandeCongeDto> refuser(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String decideurId = body != null ? body.getOrDefault("decideurId", null) : null;
        String decideurNom = body != null ? body.getOrDefault("decideurNom", null) : null;
        String commentaire = body != null ? body.getOrDefault("commentaire", null) : null;
        return ResponseEntity.ok(demandeCongeService.refuser(id, decideurId, decideurNom, commentaire));
    }
}