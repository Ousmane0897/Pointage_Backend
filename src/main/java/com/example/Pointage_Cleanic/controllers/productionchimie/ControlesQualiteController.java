package com.example.Pointage_Cleanic.controllers.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ControleQualiteDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.TendanceParametre;
import com.example.Pointage_Cleanic.Enum.DecisionControle;
import com.example.Pointage_Cleanic.entities.productionchimie.PhotoControle;
import com.example.Pointage_Cleanic.services.productionchimie.ControleQualiteService;
import com.example.Pointage_Cleanic.util.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/production-chimie/controle-qualite")
@RequiredArgsConstructor
public class ControlesQualiteController {

    private final ControleQualiteService service;
    private final ObjectMapper objectMapper;

    @GetMapping("/controles")
    public ResponseEntity<PageResponse<ControleQualiteDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String lotId,
            @RequestParam(required = false) String produitNom,
            @RequestParam(required = false) DecisionControle decision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin
    ) {
        return ResponseEntity.ok(service.list(page, size, lotId, produitNom, decision, dateDebut, dateFin));
    }

    @GetMapping("/controles/{id}")
    public ResponseEntity<ControleQualiteDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping(value = "/controles", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ControleQualiteDto> create(
            @RequestPart("controle") String controleJson,
            @RequestPart(value = "photos", required = false) MultipartFile[] photos
    ) throws IOException {
        ControleQualiteDto dto = objectMapper.readValue(controleJson, ControleQualiteDto.class);
        return ResponseEntity.status(201).body(service.create(dto, photos));
    }

    @GetMapping("/controles/{id}/photos/{index}")
    public ResponseEntity<byte[]> downloadPhoto(@PathVariable String id, @PathVariable int index) {
        PhotoControle photo = service.getPhoto(id, index);
        String mime = photo.getMimeType() == null ? MediaType.IMAGE_JPEG_VALUE : photo.getMimeType();
        String nom = photo.getNomFichier() == null ? "photo-" + index : photo.getNomFichier();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mime)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + "\"")
                .body(photo.getContenu());
    }

    @GetMapping("/tendances")
    public ResponseEntity<List<TendanceParametre>> tendances(
            @RequestParam String produitNom,
            @RequestParam(required = false) Integer nbPoints
    ) {
        return ResponseEntity.ok(service.tendances(produitNom, nbPoints));
    }
}
