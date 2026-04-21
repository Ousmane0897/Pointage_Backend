package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.AlerteRecidiveDto;
import com.example.Pointage_Cleanic.Dto.SanctionDto;
import com.example.Pointage_Cleanic.Enum.StatutSanction;
import com.example.Pointage_Cleanic.Enum.TypeSanction;
import com.example.Pointage_Cleanic.services.SanctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sanctions")
@RequiredArgsConstructor
public class SanctionController {

    private final SanctionService sanctionService;

    @PostMapping
    public ResponseEntity<SanctionDto> create(@RequestBody SanctionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sanctionService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<SanctionDto>> search(
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) TypeSanction type,
            @RequestParam(required = false) StatutSanction statut,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin
    ) {
        return ResponseEntity.ok(
                sanctionService.search(employeId, type, statut, departement, dateDebut, dateFin));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SanctionDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(sanctionService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SanctionDto> update(@PathVariable String id, @RequestBody SanctionDto dto) {
        return ResponseEntity.ok(sanctionService.update(id, dto));
    }

    @PutMapping("/{id}/statut")
    public ResponseEntity<SanctionDto> changerStatut(
            @PathVariable String id, @RequestBody Map<String, String> body
    ) {
        StatutSanction statut = StatutSanction.valueOf(body.get("statut"));
        return ResponseEntity.ok(sanctionService.changerStatut(id, statut));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        sanctionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/historique/{employeId}")
    public ResponseEntity<List<SanctionDto>> historique(@PathVariable String employeId) {
        return ResponseEntity.ok(sanctionService.historique(employeId));
    }

    @GetMapping("/alertes-recidive")
    public ResponseEntity<List<AlerteRecidiveDto>> alertesRecidive() {
        return ResponseEntity.ok(sanctionService.alertesRecidive());
    }
}