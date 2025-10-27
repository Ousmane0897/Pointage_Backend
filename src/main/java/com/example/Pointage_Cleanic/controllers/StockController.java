package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.Dto.SortieBatchRequest;
import com.example.Pointage_Cleanic.entities.stock.MouvementEntreeStock;
import com.example.Pointage_Cleanic.entities.stock.MouvementSortieStock;
import com.example.Pointage_Cleanic.services.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    // 🟢 Créer un mouvement
    @PostMapping("/mouvement")
    public MouvementEntreeStock createMouvement(@RequestBody MouvementEntreeStock mouvement) {
        return stockService.enregistrerMouvement(mouvement);
    }


    // 🟡 Stock actuel d’un produit
    @GetMapping("/produit/quantite/{codeProduit}")
    public ResponseEntity<Integer> getStock(@PathVariable String codeProduit) {
        Integer stock = stockService.getStockCurrent(codeProduit);
        return ResponseEntity.ok(stock);
        //boolean seuilAtteint = stockService.isUnderReorderPoint(codeProduit);
        //return Map.of("produitId", codeProduit, "stockActuel", stock, "enRupture", seuilAtteint);
    }

    // 🔵 Historique des mouvements
    @GetMapping("/produit/{id}/historique")
    public List<MouvementEntreeStock> historique(@PathVariable String id) {
        return stockService.getHistorique(id);
    }

    @GetMapping("/entrees")
    public ResponseEntity<List<MouvementEntreeStock>> getAll() {
        List<MouvementEntreeStock> Entrees = stockService.getAllEntree();
        return ResponseEntity.ok(Entrees);
    }


    // ✅ Sortie simple
    @PostMapping("/sortie/simple")
    public MouvementSortieStock sortie(@RequestBody MouvementSortieStock mouvement) {
        return stockService.sortieSimple(mouvement);
    }

    @PostMapping("/sorties/batch")
    public ResponseEntity<List<MouvementSortieStock>> sortieBatch(
            @RequestBody SortieBatchRequest request) {
        List<MouvementSortieStock> mouvements = stockService.sortieBatch(
                request.getMouvements(),
                request.getDestination(),
                request.getResponsable(),
                //request.getDateSortie(),
                request.getTypeMouvement(),
                request.getMotifSortieStock()
        );
        return ResponseEntity.ok(mouvements);
    }

}
