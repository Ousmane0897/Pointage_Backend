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

    @GetMapping("/sorties")
    public ResponseEntity<List<MouvementSortieStock>> getAllSorties() {
        List<MouvementSortieStock> Sorties = stockService.getAllSorties();
        return ResponseEntity.ok(Sorties);
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
                request.getBeneficaire(),
                //request.getDateSortie(),
                request.getTypeMouvement(),
                request.getMotifSortieStock()
        );
        return ResponseEntity.ok(mouvements);
    }

    // 🔵 Suivi global du stock
    @GetMapping("/suivi")
    public List<Map<String, Object>> getSuiviStock() {
        return stockService.getSuiviGlobal();
    }

    /*@GetMapping("/rapports/evolution/{codeProduit}")
    public Map<String, Object> getStockEvolution(@PathVariable String codeProduit) {
        return stockService.getStockEvolutionReport(codeProduit);
    }*/



    @GetMapping("/stats/produit-destination-mois/{nomProduit}/{destination}/{annee}")
    public Map<String, Object> quantiteProduitDestinationMensuel(
            @PathVariable String nomProduit,
            @PathVariable String destination,
            @PathVariable int annee
            ) {
        return stockService.getQuantiteProduitParDestinationParMois(nomProduit, destination, annee);
    }

    @GetMapping("/stats/consommation-destination-mois/{destination}/{annee}")
    public Map<String, Object> getConsommationParDestinationParMois(
            @PathVariable String destination,
            @PathVariable int annee) {

        return stockService.getConsommationParDestinationParMois(destination, annee);
    }



    @GetMapping("rapports/sorties-par-destination")
    public ResponseEntity<Map<String, Object>> sortiesParDestination(
            @RequestParam Integer mois,
            @RequestParam Integer annee) {
        return ResponseEntity.ok(stockService.getSortiesParDestination(mois, annee));
    }

    @GetMapping("rapports/sorties-bar-par-destination")
    public ResponseEntity<Map<String, Object>> SortiesParDestination(
            @RequestParam Integer mois,
            @RequestParam Integer annee) {
        Map<String, Object> result = stockService.getSortiesParDestinationBar(mois, annee);
        return ResponseEntity.ok(result);
    }

    @GetMapping("rapports/classement-destinations-produit")
    public ResponseEntity<Map<String, Object>> classementDestinationsProduit(
            @RequestParam String produit,
            @RequestParam Integer mois,
            @RequestParam Integer annee) {

        Map<String, Object> result = stockService.getClassementDestinationsParProduitEtMois(produit, mois, annee);
        return ResponseEntity.ok(result);
    }

    @GetMapping("rapports/consommation-produit-periode")
    public ResponseEntity<Map<String, Object>> consommationProduitParPeriode(
            @RequestParam String produit,
            @RequestParam Integer moisDebut,
            @RequestParam Integer moisFin,
            @RequestParam Integer annee) {

        Map<String, Object> result = stockService.getConsommationProduitParPeriode(produit, moisDebut, moisFin, annee);
        return ResponseEntity.ok(result);
    }



}
