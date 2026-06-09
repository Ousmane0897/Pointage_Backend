package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.RhAbsenceDto;
import com.example.Pointage_Cleanic.services.rh.RhAbsenceService;
import com.example.Pointage_Cleanic.util.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Façade RH 6.2 « Temps & Présences » — CRUD absences + pièce justificative.
 * POST/PUT en multipart (part JSON « absence » + part fichier « fichier » optionnel),
 * sur le modèle de {@code ContratController}. Réutilise {@link RhAbsenceService}.
 */
@RestController
@RequestMapping("/api/temps-presences/absences")
@RequiredArgsConstructor
public class TempsPresencesAbsenceController {

    private final RhAbsenceService rhAbsenceService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<PageResponse<RhAbsenceDto>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(PageResponse.from(rhAbsenceService.search(
                employeId, type, statut, dateDebut, dateFin, departement, q, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RhAbsenceDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(rhAbsenceService.getById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<RhAbsenceDto>> getByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(rhAbsenceService.getByEmployeId(employeId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RhAbsenceDto> create(
            @RequestPart("absence") String absenceJson,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier
    ) throws IOException {
        RhAbsenceDto dto = objectMapper.readValue(absenceJson, RhAbsenceDto.class);
        RhAbsenceDto created = rhAbsenceService.create(dto);
        if (fichier != null && !fichier.isEmpty()) {
            created = rhAbsenceService.uploadJustificatif(created.getId(), fichier);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RhAbsenceDto> update(
            @PathVariable String id,
            @RequestPart("absence") String absenceJson,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier
    ) throws IOException {
        RhAbsenceDto dto = objectMapper.readValue(absenceJson, RhAbsenceDto.class);
        RhAbsenceDto updated = rhAbsenceService.update(id, dto);
        if (fichier != null && !fichier.isEmpty()) {
            updated = rhAbsenceService.uploadJustificatif(id, fichier);
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        rhAbsenceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/justificatif", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RhAbsenceDto> uploadJustificatif(
            @PathVariable String id,
            @RequestPart("fichier") MultipartFile fichier
    ) throws IOException {
        return ResponseEntity.ok(rhAbsenceService.uploadJustificatif(id, fichier));
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
