package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.BesoinFormationDto;
import com.example.Pointage_Cleanic.Enum.rh.PrioriteBesoin;
import com.example.Pointage_Cleanic.Enum.rh.StatutBesoin;
import com.example.Pointage_Cleanic.services.rh.BesoinFormationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/besoins-formation")
@RequiredArgsConstructor
public class BesoinFormationController {

    private final BesoinFormationService besoinFormationService;

    @PostMapping
    public ResponseEntity<BesoinFormationDto> create(@RequestBody BesoinFormationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(besoinFormationService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<BesoinFormationDto>> search(
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) PrioriteBesoin priorite,
            @RequestParam(required = false) StatutBesoin statut
    ) {
        return ResponseEntity.ok(besoinFormationService.search(departement, priorite, statut));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BesoinFormationDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(besoinFormationService.getById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<BesoinFormationDto>> getByEmploye(@PathVariable String employeId) {
        return ResponseEntity.ok(besoinFormationService.getByEmploye(employeId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BesoinFormationDto> update(
            @PathVariable String id, @RequestBody BesoinFormationDto dto
    ) {
        return ResponseEntity.ok(besoinFormationService.update(id, dto));
    }

    @PutMapping("/{id}/statut")
    public ResponseEntity<BesoinFormationDto> changerStatut(
            @PathVariable String id, @RequestBody Map<String, String> body
    ) {
        StatutBesoin statut = StatutBesoin.valueOf(body.get("statut"));
        return ResponseEntity.ok(besoinFormationService.changerStatut(id, statut));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        besoinFormationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}