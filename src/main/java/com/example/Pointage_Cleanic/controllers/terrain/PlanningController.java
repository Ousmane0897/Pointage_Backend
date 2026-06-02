package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AffectationAgentDto;
import com.example.Pointage_Cleanic.Dto.terrain.ConflitAffectation;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAffectation;
import com.example.Pointage_Cleanic.services.terrain.PlanningService;
import com.example.Pointage_Cleanic.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/terrain/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService service;

    @GetMapping("/affectations")
    public PageResponse<AffectationAgentDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) StatutAffectation statut) {
        return service.list(page, size, dateDebut, dateFin, employeId, siteId, statut);
    }

    @GetMapping("/affectations/periode")
    public List<AffectationAgentDto> periode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) StatutAffectation statut) {
        return service.listePeriode(dateDebut, dateFin, employeId, siteId, statut);
    }

    @GetMapping("/affectations/conflits")
    public List<ConflitAffectation> conflits(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return service.conflits(dateDebut, dateFin);
    }

    @GetMapping("/affectations/{id}")
    public AffectationAgentDto getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping("/affectations")
    public ResponseEntity<AffectationAgentDto> create(@Valid @RequestBody AffectationAgentDto dto) {
        return ResponseEntity.status(201).body(service.create(dto));
    }

    @PutMapping("/affectations/{id}")
    public AffectationAgentDto update(@PathVariable String id, @RequestBody AffectationAgentDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/affectations/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}