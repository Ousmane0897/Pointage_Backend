package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.RhAbsenceDto;
import com.example.Pointage_Cleanic.entities.RhAbsence;
import com.example.Pointage_Cleanic.services.RhAbsenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/rh/absences")
@RequiredArgsConstructor
public class RhAbsenceController {

    private final RhAbsenceService rhAbsenceService;

    @GetMapping
    public ResponseEntity<List<RhAbsenceDto>> getAll() {
        return ResponseEntity.ok(rhAbsenceService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RhAbsenceDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(rhAbsenceService.getById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<RhAbsenceDto>> getByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(rhAbsenceService.getByEmployeId(employeId));
    }

    @PostMapping
    public ResponseEntity<RhAbsenceDto> create(@RequestBody RhAbsenceDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rhAbsenceService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RhAbsenceDto> update(@PathVariable String id, @RequestBody RhAbsenceDto dto) {
        return ResponseEntity.ok(rhAbsenceService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        rhAbsenceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/justificatif")
    public ResponseEntity<RhAbsenceDto> uploadJustificatif(
            @PathVariable String id,
            @RequestParam("fichier") MultipartFile file
    ) throws IOException {
        return ResponseEntity.ok(rhAbsenceService.uploadJustificatif(id, file));
    }

    @GetMapping("/{id}/justificatif")
    public ResponseEntity<byte[]> downloadJustificatif(@PathVariable String id) {
        RhAbsenceDto dto = rhAbsenceService.getById(id);
        byte[] data = rhAbsenceService.downloadJustificatif(id);
        String mimeType = dto.getPieceJustificative() != null
                && dto.getPieceJustificative().getMimeType() != null
                ? dto.getPieceJustificative().getMimeType() : "application/octet-stream";
        String nom = dto.getPieceJustificative() != null
                && dto.getPieceJustificative().getNom() != null
                ? dto.getPieceJustificative().getNom() : "justificatif";
        return ResponseEntity.ok()
                .header("Content-Type", mimeType)
                .header("Content-Disposition", "inline; filename=\"" + nom + "\"")
                .body(data);
    }
}