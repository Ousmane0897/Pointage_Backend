package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.PointageTerrainDto;
import com.example.Pointage_Cleanic.Enum.terrain.StatutPointage;
import com.example.Pointage_Cleanic.Enum.terrain.TypePointage;
import com.example.Pointage_Cleanic.services.terrain.PointageTerrainService;
import com.example.Pointage_Cleanic.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/terrain/pointages")
@RequiredArgsConstructor
public class PointagesTerrainController {

    private final PointageTerrainService service;

    @PostMapping
    public ResponseEntity<PointageTerrainDto> create(@Valid @RequestBody PointageTerrainDto dto) {
        return ResponseEntity.status(201).body(service.create(dto));
    }

    @GetMapping("/jour")
    public List<PointageTerrainDto> jour(@RequestParam String employeId) {
        return service.jour(employeId);
    }

    @GetMapping("/aujourd-hui")
    public List<PointageTerrainDto> aujourdHui() {
        return service.aujourdHui();
    }

    @GetMapping("/historique")
    public PageResponse<PointageTerrainDto> historique(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) TypePointage type,
            @RequestParam(required = false) StatutPointage statut) {
        return service.historique(page, size, dateDebut, dateFin, employeId, siteId, type, statut);
    }

    @GetMapping("/{id}")
    public PointageTerrainDto getById(@PathVariable String id) {
        return service.getById(id);
    }
}