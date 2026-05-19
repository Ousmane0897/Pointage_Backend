package com.example.Pointage_Cleanic.controllers.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ComparaisonPeriodes;
import com.example.Pointage_Cleanic.Dto.productionchimie.EvolutionMensuelle;
import com.example.Pointage_Cleanic.Dto.productionchimie.KpiProductionPeriode;
import com.example.Pointage_Cleanic.Dto.productionchimie.RapportTableauBord;
import com.example.Pointage_Cleanic.Dto.productionchimie.RendementProduit;
import com.example.Pointage_Cleanic.Dto.productionchimie.RepartitionStatutCq;
import com.example.Pointage_Cleanic.Dto.productionchimie.VolumeParProduit;
import com.example.Pointage_Cleanic.services.productionchimie.TableauBordProductionService;
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
@RequestMapping("/api/production-chimie/tableau-bord")
@RequiredArgsConstructor
public class TableauBordProductionController {

    private final TableauBordProductionService service;

    @GetMapping("/rapport")
    public ResponseEntity<RapportTableauBord> rapport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String produitNom,
            @RequestParam(required = false) String operateurId
    ) {
        return ResponseEntity.ok(service.rapport(dateDebut, dateFin, produitNom, operateurId));
    }

    @GetMapping("/kpis")
    public ResponseEntity<KpiProductionPeriode> kpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String produitNom,
            @RequestParam(required = false) String operateurId
    ) {
        return ResponseEntity.ok(service.kpis(dateDebut, dateFin, produitNom, operateurId));
    }

    @GetMapping("/volumes-par-produit")
    public ResponseEntity<List<VolumeParProduit>> volumesParProduit(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String produitNom
    ) {
        return ResponseEntity.ok(service.volumesParProduit(dateDebut, dateFin, produitNom));
    }

    @GetMapping("/evolution-mensuelle")
    public ResponseEntity<List<EvolutionMensuelle>> evolutionMensuelle(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String produitNom
    ) {
        return ResponseEntity.ok(service.evolutionMensuelle(dateDebut, dateFin, produitNom));
    }

    @GetMapping("/rendements")
    public ResponseEntity<List<RendementProduit>> rendements(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String produitNom,
            @RequestParam(required = false) String operateurId
    ) {
        return ResponseEntity.ok(service.rendements(dateDebut, dateFin, produitNom, operateurId));
    }

    @GetMapping("/repartition-cq")
    public ResponseEntity<RepartitionStatutCq> repartitionCq(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String produitNom
    ) {
        return ResponseEntity.ok(service.repartitionCq(dateDebut, dateFin, produitNom));
    }

    @GetMapping("/comparaison-periodes")
    public ResponseEntity<ComparaisonPeriodes> comparaison(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String produitNom,
            @RequestParam(required = false) String operateurId
    ) {
        return ResponseEntity.ok(service.comparaisonPeriodes(dateDebut, dateFin, produitNom, operateurId));
    }
}
