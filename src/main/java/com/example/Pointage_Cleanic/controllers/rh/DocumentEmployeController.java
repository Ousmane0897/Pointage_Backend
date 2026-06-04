package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.DocumentEmployeDto;
import com.example.Pointage_Cleanic.Dto.rh.ModifierDocumentRequest;
import com.example.Pointage_Cleanic.Dto.rh.ValiderDocumentRequest;
import com.example.Pointage_Cleanic.Enum.rh.CategorieDocument;
import com.example.Pointage_Cleanic.entities.rh.DocumentEmploye;
import com.example.Pointage_Cleanic.services.rh.DocumentEmployeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/gestion-personnel/documents")
@RequiredArgsConstructor
public class DocumentEmployeController {

    private final DocumentEmployeService service;

    @GetMapping
    public ResponseEntity<Page<DocumentEmployeDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String categorie
    ) {
        return ResponseEntity.ok(service.list(page, size, employeId, categorie));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<DocumentEmployeDto>> listByEmploye(@PathVariable String employeId) {
        return ResponseEntity.ok(service.listByEmploye(employeId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentEmployeDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentEmployeDto> upload(
            @RequestParam("employeId") String employeId,
            @RequestParam("nom") String nom,
            @RequestParam("categorie") CategorieDocument categorie,
            @RequestParam(value = "dateExpiration", required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateExpiration,
            @RequestParam(value = "commentaire", required = false) String commentaire,
            @RequestPart("fichier") MultipartFile fichier
    ) throws IOException {
        DocumentEmployeDto dto = DocumentEmployeDto.builder()
                .employeId(employeId)
                .nom(nom)
                .categorie(categorie)
                .dateExpiration(dateExpiration)
                .commentaire(commentaire)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(dto, fichier));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentEmployeDto> modifier(
            @PathVariable String id,
            @RequestBody ModifierDocumentRequest request
    ) {
        return ResponseEntity.ok(service.modifier(id, request));
    }

    @PutMapping("/{id}/valider")
    public ResponseEntity<DocumentEmployeDto> valider(
            @PathVariable String id,
            @RequestBody ValiderDocumentRequest request
    ) {
        return ResponseEntity.ok(service.valider(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/telecharger")
    public ResponseEntity<byte[]> telecharger(@PathVariable String id) {
        DocumentEmploye doc = service.getFichier(id);
        String mime = doc.getTypeMime() != null ? doc.getTypeMime() : "application/octet-stream";
        String nom = doc.getFichierNom() != null ? doc.getFichierNom() : "document.bin";
        return ResponseEntity.ok()
                .header("Content-Type", mime)
                .header("Content-Disposition", "attachment; filename=\"" + nom + "\"")
                .body(doc.getFichier());
    }
}
