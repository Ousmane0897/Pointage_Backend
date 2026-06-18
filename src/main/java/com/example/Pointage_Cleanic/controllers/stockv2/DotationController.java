package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComparatifDotationDto;
import com.example.Pointage_Cleanic.services.stockv2.DotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock/dotation")
@RequiredArgsConstructor
public class DotationController {

    private final DotationService service;

    @GetMapping("/comparatif")
    public ResponseEntity<ComparatifDotationDto> comparatif(
            @RequestParam String mois,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String produitId) {
        return ResponseEntity.ok(service.comparatif(mois, siteId, produitId));
    }
}
