package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.services.RecapitulatifMensuelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/recapitulatif-mensuel")
@RequiredArgsConstructor
public class RecapitulatifMensuelController {

    private final RecapitulatifMensuelService recapitulatifMensuelService;

    @GetMapping
    public ResponseEntity<List<RecapitulatifMensuelService.LigneRecapDto>> getRecapitulatif(
            @RequestParam int mois,
            @RequestParam int annee,
            @RequestParam(defaultValue = "") String departement
    ) {
        return ResponseEntity.ok(recapitulatifMensuelService.getRecapitulatif(mois, annee, departement));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam int mois,
            @RequestParam int annee,
            @RequestParam(defaultValue = "") String departement
    ) throws IOException {
        byte[] file = recapitulatifMensuelService.exportExcel(mois, annee, departement);
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=recapitulatif-" + mois + "-" + annee + ".xlsx")
                .body(file);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam int mois,
            @RequestParam int annee,
            @RequestParam(defaultValue = "") String departement
    ) throws IOException {
        byte[] file = recapitulatifMensuelService.exportPdf(mois, annee, departement);
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=recapitulatif-" + mois + "-" + annee + ".pdf")
                .body(file);
    }
}