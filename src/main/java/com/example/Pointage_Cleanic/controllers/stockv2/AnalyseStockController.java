package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MatriceComparatifDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ResultatCroiseDto;
import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseConsoMensuelleDto;
import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseDonsDto;
import com.example.Pointage_Cleanic.Enum.stockv2.AxeAnalyse;
import com.example.Pointage_Cleanic.Enum.stockv2.AxeComparatif;
import com.example.Pointage_Cleanic.Enum.stockv2.MesureCroise;
import com.example.Pointage_Cleanic.Enum.stockv2.NatureDon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.services.stockv2.AnalyseDonsService;
import com.example.Pointage_Cleanic.services.stockv2.AnalyseMensuelleService;
import com.example.Pointage_Cleanic.services.stockv2.ComparatifAnalyseService;
import com.example.Pointage_Cleanic.services.stockv2.FiltreCroiseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stock/analyse")
@RequiredArgsConstructor
public class AnalyseStockController {

    private final AnalyseMensuelleService mensuelleService;
    private final AnalyseDonsService donsService;
    private final ComparatifAnalyseService comparatifService;
    private final FiltreCroiseService croiseService;

    @GetMapping("/mensuel")
    public ResponseEntity<SyntheseConsoMensuelleDto> mensuel(
            @RequestParam String mois,
            @RequestParam(required = false) String moisFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String categorieId) {
        return ResponseEntity.ok(mensuelleService.synthese(mois, moisFin, siteId, categorieId));
    }

    @GetMapping("/dons")
    public ResponseEntity<SyntheseDonsDto> dons(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) NatureDon natureDon,
            @RequestParam(required = false) String beneficiaire,
            @RequestParam(required = false) String siteId) {
        return ResponseEntity.ok(donsService.synthese(dateDebut, dateFin, natureDon, beneficiaire, siteId));
    }

    @GetMapping("/comparatif")
    public ResponseEntity<MatriceComparatifDto> comparatif(
            @RequestParam AxeComparatif axe,
            @RequestParam String dateDebut,
            @RequestParam String dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String categorieId,
            @RequestParam(required = false) TypeSortie typeSortie,
            @RequestParam double seuilPct) {
        return ResponseEntity.ok(
                comparatifService.comparatif(axe, dateDebut, dateFin, siteId, categorieId, typeSortie, seuilPct));
    }

    @GetMapping("/croise")
    public ResponseEntity<ResultatCroiseDto> croise(
            @RequestParam AxeAnalyse axeLignes,
            @RequestParam(required = false) AxeAnalyse axeColonnes,
            @RequestParam MesureCroise mesure,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String produitId,
            @RequestParam(required = false) String categorieId,
            @RequestParam(required = false) TypeSortie typeSortie) {
        return ResponseEntity.ok(croiseService.croise(
                axeLignes, axeColonnes, mesure, dateDebut, dateFin, siteId, produitId, categorieId, typeSortie));
    }
}
