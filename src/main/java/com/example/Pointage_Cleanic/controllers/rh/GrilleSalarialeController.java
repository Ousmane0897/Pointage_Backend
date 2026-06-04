package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.CategorieProfessionnelleDto;
import com.example.Pointage_Cleanic.Enum.rh.RegimeIpres;
import com.example.Pointage_Cleanic.services.rh.CategorieProfessionnelleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/grille-salariale")
@RequiredArgsConstructor
public class GrilleSalarialeController {

    private final CategorieProfessionnelleService categorieProfessionnelleService;

    @GetMapping
    public ResponseEntity<List<CategorieProfessionnelleDto>> getAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) RegimeIpres regimeIpres,
            @RequestParam(required = false) Boolean actif
    ) {
        return ResponseEntity.ok(categorieProfessionnelleService.getAll(q, regimeIpres, actif));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategorieProfessionnelleDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(categorieProfessionnelleService.getById(id));
    }

    @PostMapping
    public ResponseEntity<CategorieProfessionnelleDto> create(@RequestBody CategorieProfessionnelleDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categorieProfessionnelleService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorieProfessionnelleDto> update(
            @PathVariable String id,
            @RequestBody CategorieProfessionnelleDto dto
    ) {
        return ResponseEntity.ok(categorieProfessionnelleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        categorieProfessionnelleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}