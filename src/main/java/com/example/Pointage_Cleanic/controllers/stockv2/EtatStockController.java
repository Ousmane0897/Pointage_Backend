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
import org.springframework.web.bind.annotation.PathVariable;
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

    /**
     * État de stock d'un seul produit — sert les colonnes de stock des bons (7.4) : « Reste » sur
     * un bon de sortie, « Stock actuel » sur un bon d'entrée. {@code siteId} absent ⇒ solde
     * consolidé tous sites.
     */
    @GetMapping("/produit/{produitId}")
    public ResponseEntity<EtatStockDto> getParProduit(@PathVariable String produitId,
                                                      @RequestParam(required = false) String siteId) {
        return ResponseEntity.ok(service.getParProduit(produitId, siteId));
    }

    @PutMapping("/seuils")
    public ResponseEntity<EtatStockDto> majSeuil(@RequestBody SeuilPayload payload) {
        return ResponseEntity.ok(service.majSeuil(payload));
    }
}
