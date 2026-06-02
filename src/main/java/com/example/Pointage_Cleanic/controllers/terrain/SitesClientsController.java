package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.SiteClientDto;
import com.example.Pointage_Cleanic.Enum.terrain.FrequencePassage;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.services.terrain.SiteClientService;
import com.example.Pointage_Cleanic.util.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/terrain/sites-clients")
@RequiredArgsConstructor
public class SitesClientsController {

    private final SiteClientService service;
    private final ObjectMapper objectMapper;

    @GetMapping
    public PageResponse<SiteClientDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String ville,
            @RequestParam(required = false) FrequencePassage frequencePassage,
            @RequestParam(required = false) Boolean actif) {
        return service.list(page, size, q, ville, frequencePassage, actif);
    }

    @GetMapping("/actifs")
    public List<SiteClientDto> listActifs() {
        return service.listActifs();
    }

    @GetMapping("/{id}")
    public SiteClientDto getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SiteClientDto> create(
            @RequestPart("site") String siteJson,
            @RequestPart(value = "cahierDesCharges", required = false) MultipartFile cahierDesCharges
    ) throws IOException {
        SiteClientDto dto = objectMapper.readValue(siteJson, SiteClientDto.class);
        return ResponseEntity.status(201).body(service.create(dto, cahierDesCharges));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SiteClientDto update(
            @PathVariable String id,
            @RequestPart("site") String siteJson,
            @RequestPart(value = "cahierDesCharges", required = false) MultipartFile cahierDesCharges
    ) throws IOException {
        SiteClientDto dto = objectMapper.readValue(siteJson, SiteClientDto.class);
        return service.update(id, dto, cahierDesCharges);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/cahier-charges")
    public ResponseEntity<byte[]> cahierCharges(@PathVariable String id) {
        SiteClient site = service.getCahierCharges(id);
        String mime = site.getCahierDesChargesMimeType() == null
                ? MediaType.APPLICATION_PDF_VALUE : site.getCahierDesChargesMimeType();
        String nom = site.getCahierDesChargesNomFichier() == null
                ? "cahier-charges.pdf" : site.getCahierDesChargesNomFichier();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mime)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + "\"")
                .body(site.getCahierDesChargesFichier());
    }
}