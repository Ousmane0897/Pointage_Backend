package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.RapportTableauBordStockDto;
import com.example.Pointage_Cleanic.services.stockv2.TableauBordStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stock/tableau-bord")
@RequiredArgsConstructor
public class TableauBordStockController {

    private final TableauBordStockService service;

    @GetMapping
    public ResponseEntity<RapportTableauBordStockDto> rapport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String categorieId,
            @RequestParam(required = false) Integer moisDormance
    ) {
        return ResponseEntity.ok(service.rapport(dateDebut, dateFin, siteId, categorieId, moisDormance));
    }
}
