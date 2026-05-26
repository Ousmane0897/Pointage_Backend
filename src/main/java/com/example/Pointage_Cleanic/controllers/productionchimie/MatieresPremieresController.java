package com.example.Pointage_Cleanic.controllers.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.MatierePremiereDto;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import com.example.Pointage_Cleanic.services.productionchimie.MatierePremiereService;
import com.example.Pointage_Cleanic.util.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/production-chimie/stock-chimie/matieres-premieres")
@RequiredArgsConstructor
public class MatieresPremieresController {

    private final MatierePremiereService service;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<PageResponse<MatierePremiereDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean sousSeuilCritique,
            @RequestParam(required = false) Boolean actif
    ) {
        return ResponseEntity.ok(service.list(page, size, q, sousSeuilCritique, actif));
    }

    @GetMapping("/actives")
    public ResponseEntity<List<MatierePremiereDto>> listActives() {
        return ResponseEntity.ok(service.listActives());
    }

    @GetMapping("/sous-seuil")
    public ResponseEntity<List<MatierePremiereDto>> listSousSeuil() {
        return ResponseEntity.ok(service.listSousSeuil());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatierePremiereDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MatierePremiereDto> create(
            @RequestPart("matiere") String matiereJson,
            @RequestPart(value = "ficheSecurite", required = false) MultipartFile ficheSecurite
    ) throws IOException {
        MatierePremiereDto dto = objectMapper.readValue(matiereJson, MatierePremiereDto.class);
        MatierePremiereDto created = service.create(dto, ficheSecurite);
        return ResponseEntity.status(201).body(created);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MatierePremiereDto> update(
            @PathVariable String id,
            @RequestPart("matiere") String matiereJson,
            @RequestPart(value = "ficheSecurite", required = false) MultipartFile ficheSecurite,
            @RequestParam(value = "supprimerFicheSecurite", defaultValue = "false") boolean supprimerFicheSecurite
    ) throws IOException {
        MatierePremiereDto dto = objectMapper.readValue(matiereJson, MatierePremiereDto.class);
        return ResponseEntity.ok(service.update(id, dto, ficheSecurite, supprimerFicheSecurite));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/fiche-securite")
    public ResponseEntity<byte[]> downloadFicheSecurite(@PathVariable String id) {
        MatierePremiere mp = service.getFicheSecurite(id);
        String mime = mp.getFicheSecuriteMimeType() == null ? MediaType.APPLICATION_PDF_VALUE : mp.getFicheSecuriteMimeType();
        String nom = mp.getFicheSecuriteNom() == null ? "fiche-securite.pdf" : mp.getFicheSecuriteNom();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mime)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nom + "\"")
                .body(mp.getFicheSecurite());
    }

    @DeleteMapping("/{id}/fiche-securite")
    public ResponseEntity<Void> deleteFicheSecurite(@PathVariable String id) {
        service.deleteFicheSecurite(id);
        return ResponseEntity.noContent().build();
    }
}