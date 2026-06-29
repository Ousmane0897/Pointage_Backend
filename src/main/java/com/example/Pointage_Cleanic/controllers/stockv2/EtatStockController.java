package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.EtatStockDto;
import com.example.Pointage_Cleanic.Dto.stockv2.SeuilPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutStock;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.services.stockv2.EtatStockService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock/etat-stock")
@RequiredArgsConstructor
public class EtatStockController {

    private final EtatStockService service;

    @GetMapping
    public ResponseEntity<PageResponse<EtatStockDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categorieId,
            @RequestParam(required = false) TypeProduit typeProduit,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) StatutStock statut,
            @RequestParam(required = false) Boolean parSite
    ) {
        return ResponseEntity.ok(service.list(page, size, q, categorieId, typeProduit, siteId, statut, parSite));
    }

    @PutMapping("/seuils")
    public ResponseEntity<EtatStockDto> majSeuil(@RequestBody SeuilPayload payload) {
        return ResponseEntity.ok(service.majSeuil(payload));
    }
}
