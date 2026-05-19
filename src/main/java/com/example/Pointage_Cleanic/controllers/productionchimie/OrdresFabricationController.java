package com.example.Pointage_Cleanic.controllers.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.AnnulerOfPayload;
import com.example.Pointage_Cleanic.Dto.productionchimie.DisponibiliteOf;
import com.example.Pointage_Cleanic.Dto.productionchimie.LancerOfPayload;
import com.example.Pointage_Cleanic.Dto.productionchimie.OrdreFabricationDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.TerminerOfPayload;
import com.example.Pointage_Cleanic.Enum.StatutOf;
import com.example.Pointage_Cleanic.services.productionchimie.OrdreFabricationService;
import com.example.Pointage_Cleanic.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/production-chimie/ordres")
@RequiredArgsConstructor
public class OrdresFabricationController {

    private final OrdreFabricationService service;

    @GetMapping
    public ResponseEntity<PageResponse<OrdreFabricationDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatutOf statut,
            @RequestParam(required = false) String operateurId,
            @RequestParam(required = false) String formulationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin
    ) {
        return ResponseEntity.ok(service.list(page, size, q, statut, operateurId, formulationId, dateDebut, dateFin));
    }

    @GetMapping("/kanban")
    public ResponseEntity<List<OrdreFabricationDto>> kanban() {
        return ResponseEntity.ok(service.kanban());
    }

    @GetMapping("/disponibilite-mp")
    public ResponseEntity<DisponibiliteOf> disponibilite(
            @RequestParam String formulationId,
            @RequestParam Double quantiteCible
    ) {
        return ResponseEntity.ok(service.disponibiliteForFormulation(formulationId, quantiteCible));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrdreFabricationDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/{id}/disponibilite-mp")
    public ResponseEntity<DisponibiliteOf> disponibiliteForOf(@PathVariable String id) {
        return ResponseEntity.ok(service.disponibiliteForOf(id));
    }

    @PostMapping
    public ResponseEntity<OrdreFabricationDto> create(@Valid @RequestBody OrdreFabricationDto dto) {
        return ResponseEntity.status(201).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrdreFabricationDto> update(
            @PathVariable String id,
            @Valid @RequestBody OrdreFabricationDto dto
    ) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PostMapping("/{id}/lancer")
    public ResponseEntity<OrdreFabricationDto> lancer(
            @PathVariable String id,
            @RequestBody(required = false) LancerOfPayload payload
    ) {
        return ResponseEntity.ok(service.lancer(id, payload));
    }

    @PostMapping("/{id}/terminer")
    public ResponseEntity<OrdreFabricationDto> terminer(
            @PathVariable String id,
            @RequestBody(required = false) TerminerOfPayload payload
    ) {
        return ResponseEntity.ok(service.terminer(id, payload));
    }

    @PostMapping("/{id}/annuler")
    public ResponseEntity<OrdreFabricationDto> annuler(
            @PathVariable String id,
            @Valid @RequestBody AnnulerOfPayload payload
    ) {
        return ResponseEntity.ok(service.annuler(id, payload));
    }
}
