package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.SuggestionApproDto;
import com.example.Pointage_Cleanic.services.stockv2.ApprovisionnementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock/approvisionnement")
@RequiredArgsConstructor
public class ApprovisionnementController {

    private final ApprovisionnementService service;

    @GetMapping("/suggestions")
    public ResponseEntity<List<SuggestionApproDto>> suggestions(
            @RequestParam(required = false) Integer nMois,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String categorieId,
            @RequestParam(required = false) String fournisseur
    ) {
        return ResponseEntity.ok(service.suggestions(nMois, siteId, categorieId, fournisseur));
    }
}
