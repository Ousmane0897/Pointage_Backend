package com.example.Pointage_Cleanic.controllers.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.FormatConditionnementDto;
import com.example.Pointage_Cleanic.Enum.TypeContenant;
import com.example.Pointage_Cleanic.services.productionchimie.FormatConditionnementService;
import com.example.Pointage_Cleanic.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/production-chimie/formats-conditionnement")
@RequiredArgsConstructor
public class FormatsConditionnementController {

    private final FormatConditionnementService service;

    @GetMapping
    public ResponseEntity<PageResponse<FormatConditionnementDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TypeContenant typeContenant,
            @RequestParam(required = false) Boolean actif
    ) {
        return ResponseEntity.ok(service.list(page, size, q, typeContenant, actif));
    }

    @GetMapping("/actifs")
    public ResponseEntity<List<FormatConditionnementDto>> listActifs() {
        return ResponseEntity.ok(service.listActifs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormatConditionnementDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<FormatConditionnementDto> create(@Valid @RequestBody FormatConditionnementDto dto) {
        return ResponseEntity.status(201).body(service.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormatConditionnementDto> update(
            @PathVariable String id,
            @Valid @RequestBody FormatConditionnementDto dto
    ) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}