package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.HeureSupplementaireDto;
import com.example.Pointage_Cleanic.services.HeureSupplementaireService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/heures-supplementaires")
@RequiredArgsConstructor
public class HeureSupplementaireController {

    private final HeureSupplementaireService heureSupplementaireService;

    @GetMapping
    public ResponseEntity<List<HeureSupplementaireDto>> getAll() {
        return ResponseEntity.ok(heureSupplementaireService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<HeureSupplementaireDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(heureSupplementaireService.getById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<HeureSupplementaireDto>> getByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(heureSupplementaireService.getByEmployeId(employeId));
    }

    @PostMapping
    public ResponseEntity<HeureSupplementaireDto> create(@RequestBody HeureSupplementaireDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(heureSupplementaireService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HeureSupplementaireDto> update(
            @PathVariable String id, @RequestBody HeureSupplementaireDto dto) {
        return ResponseEntity.ok(heureSupplementaireService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        heureSupplementaireService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/valider")
    public ResponseEntity<HeureSupplementaireDto> valider(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String decideurId = body != null ? body.getOrDefault("decideurId", null) : null;
        String decideurNom = body != null ? body.getOrDefault("decideurNom", null) : null;
        String commentaire = body != null ? body.getOrDefault("commentaire", null) : null;
        return ResponseEntity.ok(heureSupplementaireService.valider(id, decideurId, decideurNom, commentaire));
    }

    @PutMapping("/{id}/refuser")
    public ResponseEntity<HeureSupplementaireDto> refuser(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String decideurId = body != null ? body.getOrDefault("decideurId", null) : null;
        String decideurNom = body != null ? body.getOrDefault("decideurNom", null) : null;
        String commentaire = body != null ? body.getOrDefault("commentaire", null) : null;
        return ResponseEntity.ok(heureSupplementaireService.refuser(id, decideurId, decideurNom, commentaire));
    }
}