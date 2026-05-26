package com.example.Pointage_Cleanic.controllers.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ComparaisonVersions;
import com.example.Pointage_Cleanic.Dto.productionchimie.FicheFormulationDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.RestaurerVersionPayload;
import com.example.Pointage_Cleanic.Enum.StatutFormulation;
import com.example.Pointage_Cleanic.services.productionchimie.FormulationService;
import com.example.Pointage_Cleanic.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/production-chimie/formulations")
@RequiredArgsConstructor
public class FormulationsController {

    private final FormulationService service;

    @GetMapping
    public ResponseEntity<PageResponse<FicheFormulationDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatutFormulation statut
    ) {
        return ResponseEntity.ok(service.list(page, size, q, statut));
    }

    @GetMapping("/validees")
    public ResponseEntity<List<FicheFormulationDto>> listValidees() {
        return ResponseEntity.ok(service.listValidees());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FicheFormulationDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<FicheFormulationDto> create(@Valid @RequestBody FicheFormulationDto dto) {
        return ResponseEntity.status(201).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FicheFormulationDto> update(
            @PathVariable String id,
            @Valid @RequestBody FicheFormulationDto dto
    ) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/versions/{numero}/restaurer")
    public ResponseEntity<FicheFormulationDto> restaurer(
            @PathVariable String id,
            @PathVariable Integer numero,
            @RequestBody(required = false) RestaurerVersionPayload payload
    ) {
        return ResponseEntity.ok(service.restaurerVersion(id, numero, payload));
    }

    @GetMapping("/{id}/versions/comparer")
    public ResponseEntity<ComparaisonVersions> comparer(
            @PathVariable String id,
            @RequestParam Integer v1,
            @RequestParam Integer v2
    ) {
        return ResponseEntity.ok(service.comparerVersions(id, v1, v2));
    }
}