package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MouvementPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.MouvementStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.services.stockv2.MouvementStockService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stock/mouvements")
@RequiredArgsConstructor
public class MouvementStockController {

    private final MouvementStockService service;

    @GetMapping
    public ResponseEntity<PageResponse<MouvementStockDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String produitId,
            @RequestParam(required = false) TypeMouvement type,
            @RequestParam(required = false) MotifMouvement motif,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin
    ) {
        return ResponseEntity.ok(service.list(page, size, q, produitId, type, motif, siteId, dateDebut, dateFin));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MouvementStockDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<MouvementStockDto> create(@RequestBody MouvementPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(payload));
    }
}
