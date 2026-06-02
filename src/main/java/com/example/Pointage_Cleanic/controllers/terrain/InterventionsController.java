package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.FicheInterventionDto;
import com.example.Pointage_Cleanic.Enum.terrain.StatutIntervention;
import com.example.Pointage_Cleanic.entities.terrain.PhotoInterventionFichier;
import com.example.Pointage_Cleanic.services.terrain.InterventionService;
import com.example.Pointage_Cleanic.util.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/terrain/interventions")
@RequiredArgsConstructor
public class InterventionsController {

    private final InterventionService service;
    private final ObjectMapper objectMapper;

    @GetMapping
    public PageResponse<FicheInterventionDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) StatutIntervention statut) {
        return service.list(page, size, dateDebut, dateFin, siteId, employeId, statut);
    }

    @GetMapping("/{id}")
    public FicheInterventionDto getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FicheInterventionDto> create(
            @RequestPart("fiche") String ficheJson,
            @RequestPart(value = "photos", required = false) MultipartFile[] photos
    ) throws IOException {
        FicheInterventionDto dto = objectMapper.readValue(ficheJson, FicheInterventionDto.class);
        return ResponseEntity.status(201).body(service.create(dto, photos));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FicheInterventionDto update(
            @PathVariable String id,
            @RequestPart("fiche") String ficheJson,
            @RequestPart(value = "photos", required = false) MultipartFile[] photos
    ) throws IOException {
        FicheInterventionDto dto = objectMapper.readValue(ficheJson, FicheInterventionDto.class);
        return service.update(id, dto, photos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/photos/{index}")
    public ResponseEntity<byte[]> photo(@PathVariable String id, @PathVariable int index) {
        PhotoInterventionFichier photo = service.getPhoto(id, index);
        String mime = photo.getMimeType() == null ? MediaType.IMAGE_JPEG_VALUE : photo.getMimeType();
        String nom = photo.getNomFichier() == null ? "photo-" + index : photo.getNomFichier();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mime)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + "\"")
                .body(photo.getContenu());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String id) {
        byte[] pdf = service.genererPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"intervention-" + id + ".pdf\"")
                .body(pdf);
    }
}