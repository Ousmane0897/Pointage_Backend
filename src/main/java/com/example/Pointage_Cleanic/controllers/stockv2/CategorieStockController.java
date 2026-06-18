package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.CategoriePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.CategorieStockDto;
import com.example.Pointage_Cleanic.services.stockv2.CategorieStockService;
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
@RequestMapping("/api/stock/categories")
@RequiredArgsConstructor
public class CategorieStockController {

    private final CategorieStockService service;

    @GetMapping("/racines")
    public ResponseEntity<List<CategorieStockDto>> racines() {
        return ResponseEntity.ok(service.racines());
    }

    @GetMapping("/enfants")
    public ResponseEntity<List<CategorieStockDto>> enfants(@RequestParam String parentId) {
        return ResponseEntity.ok(service.enfants(parentId));
    }

    @GetMapping
    public ResponseEntity<List<CategorieStockDto>> liste() {
        return ResponseEntity.ok(service.liste());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategorieStockDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<CategorieStockDto> create(@RequestBody CategoriePayload payload) {
        return ResponseEntity.ok(service.create(payload));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorieStockDto> update(@PathVariable String id, @RequestBody CategoriePayload payload) {
        return ResponseEntity.ok(service.update(id, payload));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
