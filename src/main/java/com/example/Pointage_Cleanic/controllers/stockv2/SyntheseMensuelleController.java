package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseMensuelleDto;
import com.example.Pointage_Cleanic.services.stockv2.SyntheseMensuelleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock/synthese-mensuelle")
@RequiredArgsConstructor
public class SyntheseMensuelleController {

    private final SyntheseMensuelleService service;

    @GetMapping
    public ResponseEntity<SyntheseMensuelleDto> synthese(
            @RequestParam String mois,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String categorieId
    ) {
        return ResponseEntity.ok(service.synthese(mois, siteId, categorieId));
    }
}
