package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ConsommationDestinataireDto;
import com.example.Pointage_Cleanic.Dto.stockv2.RapportConsommationDto;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeRapportConsommation;
import com.example.Pointage_Cleanic.services.stockv2.ConsommationStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stock/consommation")
@RequiredArgsConstructor
public class ConsommationController {

    private final ConsommationStockService service;

    @GetMapping("/par-destinataire")
    public ResponseEntity<List<ConsommationDestinataireDto>> parDestinataire(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String produitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.parDestinataire(siteId, produitId, dateDebut, dateFin));
    }

    @GetMapping("/rapport")
    public ResponseEntity<RapportConsommationDto> rapport(
            @RequestParam TypeRapportConsommation type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String produitId,
            @RequestParam(required = false) String categorieId) {
        return ResponseEntity.ok(service.rapport(type, dateDebut, dateFin, siteId, produitId, categorieId));
    }
}
