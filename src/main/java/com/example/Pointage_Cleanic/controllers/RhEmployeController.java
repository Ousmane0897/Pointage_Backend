package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Dto.RhEmployeStatutRequest;
import com.example.Pointage_Cleanic.services.RhEmployeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/employes")
@RequiredArgsConstructor
public class RhEmployeController {

    private final RhEmployeService rhEmployeService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<Page<EmployeCompletDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String statut
    ) {
        return ResponseEntity.ok(rhEmployeService.list(page, size, q, statut));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeCompletDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(rhEmployeService.getById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeCompletDto> create(
            @RequestPart("employe") String employeJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "contrat", required = false) MultipartFile contrat
    ) throws IOException {
        EmployeCompletDto dto = objectMapper.readValue(employeJson, EmployeCompletDto.class);
        return ResponseEntity.status(HttpStatus.CREATED).body(rhEmployeService.create(dto, photo, contrat));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeCompletDto> update(
            @PathVariable String id,
            @RequestPart("employe") String employeJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) throws IOException {
        EmployeCompletDto dto = objectMapper.readValue(employeJson, EmployeCompletDto.class);
        return ResponseEntity.ok(rhEmployeService.update(id, dto, photo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        rhEmployeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/statut")
    public ResponseEntity<EmployeCompletDto> updateStatut(
            @PathVariable String id,
            @RequestBody RhEmployeStatutRequest request
    ) {
        return ResponseEntity.ok(rhEmployeService.updateStatut(id, request));
    }
}
