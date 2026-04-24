package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.*;
import com.example.Pointage_Cleanic.entities.Contrat;
import com.example.Pointage_Cleanic.services.ContratService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/contrats")
@RequiredArgsConstructor
public class ContratController {

    private final ContratService contratService;
    private final ObjectMapper objectMapper;

    @GetMapping("/alertes-echeance")
    public ResponseEntity<List<AlerteContratDto>> getAlertesEcheance() {
        return ResponseEntity.ok(contratService.getAlertesEcheance());
    }

    @GetMapping
    public ResponseEntity<List<ContratDto>> getAll() {
        return ResponseEntity.ok(contratService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContratDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(contratService.getById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<ContratDto>> getByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(contratService.getByEmployeId(employeId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContratDto> create(
            @RequestPart("contrat") String contratJson,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier
    ) throws IOException {
        ContratDto dto = objectMapper.readValue(contratJson, ContratDto.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(contratService.create(dto, fichier));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContratDto> update(
            @PathVariable String id,
            @RequestPart("contrat") String contratJson,
            @RequestPart(value = "fichier", required = false) MultipartFile fichier
    ) throws IOException {
        ContratDto dto = objectMapper.readValue(contratJson, ContratDto.class);
        return ResponseEntity.ok(contratService.update(id, dto, fichier));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        contratService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/renouveler")
    public ResponseEntity<ContratDto> renouveler(
            @PathVariable String id,
            @RequestBody RenouvellerContratRequest request
    ) {
        return ResponseEntity.ok(contratService.renouveler(id, request));
    }

    @PostMapping("/{id}/avenants")
    public ResponseEntity<ContratDto> ajouterAvenant(
            @PathVariable String id,
            @RequestBody AjouterAvenantRequest request
    ) {
        return ResponseEntity.ok(contratService.ajouterAvenant(id, request));
    }

    @PutMapping("/{id}/resilier")
    public ResponseEntity<ContratDto> resilier(@PathVariable String id) {
        return ResponseEntity.ok(contratService.resilier(id));
    }

    @GetMapping("/{id}/fichier")
    public ResponseEntity<byte[]> getFichier(@PathVariable String id) {
        Contrat contrat = contratService.getFichier(id);
        byte[] data = contrat.getFichierContrat();
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        String mime = contrat.getFichierContratMimeType() != null
                ? contrat.getFichierContratMimeType() : "application/pdf";
        String nom = contrat.getFichierContratNom() != null
                ? contrat.getFichierContratNom() : "contrat.pdf";
        return ResponseEntity.ok()
                .header("Content-Type", mime)
                .header("Content-Disposition", "inline; filename=\"" + nom + "\"")
                .body(data);
    }

    @DeleteMapping("/{id}/fichier")
    public ResponseEntity<Void> deleteFichier(@PathVariable String id) {
        contratService.deleteFichier(id);
        return ResponseEntity.noContent().build();
    }
}