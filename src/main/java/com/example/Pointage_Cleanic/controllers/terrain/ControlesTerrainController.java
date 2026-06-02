package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.ControleQualiteTerrainDto;
import com.example.Pointage_Cleanic.Dto.terrain.EvolutionNotePoint;
import com.example.Pointage_Cleanic.Dto.terrain.GrilleEvaluationTerrainDto;
import com.example.Pointage_Cleanic.Enum.terrain.DecisionControleTerrain;
import com.example.Pointage_Cleanic.entities.terrain.PhotoControleFichier;
import com.example.Pointage_Cleanic.services.terrain.ControleQualiteService;
import com.example.Pointage_Cleanic.util.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/terrain/controles-terrain")
@RequiredArgsConstructor
public class ControlesTerrainController {

    private final ControleQualiteService service;
    private final ObjectMapper objectMapper;

    // ───────────────────────── Grilles ─────────────────────────

    @GetMapping("/grilles")
    public List<GrilleEvaluationTerrainDto> listGrilles() {
        return service.listGrilles();
    }

    @GetMapping("/grilles/pour-site/{siteId}")
    public ResponseEntity<GrilleEvaluationTerrainDto> pourSite(@PathVariable String siteId) {
        return ResponseEntity.ok(service.pourSite(siteId));
    }

    @GetMapping("/grilles/{id}")
    public GrilleEvaluationTerrainDto getGrille(@PathVariable String id) {
        return service.getGrille(id);
    }

    @PostMapping("/grilles")
    public ResponseEntity<GrilleEvaluationTerrainDto> createGrille(@Valid @RequestBody GrilleEvaluationTerrainDto dto) {
        return ResponseEntity.status(201).body(service.createGrille(dto));
    }

    @PutMapping("/grilles/{id}")
    public GrilleEvaluationTerrainDto updateGrille(@PathVariable String id,
                                                   @RequestBody GrilleEvaluationTerrainDto dto) {
        return service.updateGrille(id, dto);
    }

    @DeleteMapping("/grilles/{id}")
    public ResponseEntity<Void> deleteGrille(@PathVariable String id) {
        service.deleteGrille(id);
        return ResponseEntity.noContent().build();
    }

    // ───────────────────────── Contrôles ─────────────────────────

    @GetMapping
    public PageResponse<ControleQualiteTerrainDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) DecisionControleTerrain decision,
            @RequestParam(required = false) String controleurEmployeId) {
        return service.list(page, size, dateDebut, dateFin, decision, controleurEmployeId, siteId);
    }

    @GetMapping("/historique/{siteId}")
    public List<EvolutionNotePoint> historique(@PathVariable String siteId,
                                               @RequestParam(defaultValue = "12") int nbPoints) {
        return service.historique(siteId, nbPoints);
    }

    @GetMapping("/{id}")
    public ControleQualiteTerrainDto getControle(@PathVariable String id) {
        return service.getControle(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ControleQualiteTerrainDto> create(
            @RequestPart("controle") String controleJson,
            @RequestPart(value = "photos", required = false) MultipartFile[] photos
    ) throws IOException {
        ControleQualiteTerrainDto dto = objectMapper.readValue(controleJson, ControleQualiteTerrainDto.class);
        return ResponseEntity.status(201).body(service.create(dto, photos));
    }

    @GetMapping("/{id}/photos/{index}")
    public ResponseEntity<byte[]> photo(@PathVariable String id, @PathVariable int index) {
        PhotoControleFichier photo = service.getPhoto(id, index);
        String mime = photo.getMimeType() == null ? MediaType.IMAGE_JPEG_VALUE : photo.getMimeType();
        String nom = photo.getNomFichier() == null ? "photo-" + index : photo.getNomFichier();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mime)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + "\"")
                .body(photo.getContenu());
    }
}