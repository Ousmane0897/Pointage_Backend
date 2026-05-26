package com.example.Pointage_Cleanic.controllers.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.LotDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.TracabiliteLot;
import com.example.Pointage_Cleanic.Enum.StatutControleLot;
import com.example.Pointage_Cleanic.Enum.StatutStockLot;
import com.example.Pointage_Cleanic.services.productionchimie.LotService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/production-chimie/lots")
@RequiredArgsConstructor
public class LotsController {

    private final LotService service;

    @GetMapping
    public ResponseEntity<PageResponse<LotDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String produitNom,
            @RequestParam(required = false) String formulationId,
            @RequestParam(required = false) StatutControleLot statutControle,
            @RequestParam(required = false) StatutStockLot statutStock,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin
    ) {
        return ResponseEntity.ok(service.list(page, size, q, produitNom, formulationId, statutControle, statutStock, dateDebut, dateFin));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LotDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/{id}/tracabilite")
    public ResponseEntity<TracabiliteLot> tracabilite(@PathVariable String id) {
        return ResponseEntity.ok(service.tracabilite(id));
    }
}
