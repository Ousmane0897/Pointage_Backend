package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.ComparaisonPeriodesTerrain;
import com.example.Pointage_Cleanic.Dto.terrain.IncidentsParSite;
import com.example.Pointage_Cleanic.Dto.terrain.InterventionsParSite;
import com.example.Pointage_Cleanic.Dto.terrain.KpiTerrain;
import com.example.Pointage_Cleanic.Dto.terrain.PointEvolution;
import com.example.Pointage_Cleanic.Dto.terrain.RapportTableauBordTerrain;
import com.example.Pointage_Cleanic.Dto.terrain.SatisfactionParSite;
import com.example.Pointage_Cleanic.services.terrain.TableauBordTerrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/terrain/tableau-bord")
@RequiredArgsConstructor
public class TableauBordTerrainController {

    private final TableauBordTerrainService service;

    @GetMapping("/rapport")
    public RapportTableauBordTerrain rapport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String typeIntervention) {
        return service.rapport(dateDebut, dateFin, siteId, employeId, typeIntervention);
    }

    @GetMapping("/kpis")
    public KpiTerrain kpis(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String typeIntervention) {
        return service.kpis(dateDebut, dateFin, siteId, employeId, typeIntervention);
    }

    @GetMapping("/interventions-par-site")
    public List<InterventionsParSite> interventionsParSite(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String typeIntervention) {
        return service.interventionsParSite(dateDebut, dateFin, siteId, employeId);
    }

    @GetMapping("/evolution-couverture")
    public List<PointEvolution> evolutionCouverture(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String typeIntervention) {
        return service.evolutionCouverture(dateDebut, dateFin, siteId, employeId);
    }

    @GetMapping("/incidents-par-site")
    public List<IncidentsParSite> incidentsParSite(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String typeIntervention) {
        return service.incidentsParSite(dateDebut, dateFin, siteId, employeId);
    }

    @GetMapping("/evolution-satisfaction")
    public List<PointEvolution> evolutionSatisfaction(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String typeIntervention) {
        return service.evolutionSatisfaction(dateDebut, dateFin, siteId);
    }

    @GetMapping("/satisfaction-par-site")
    public List<SatisfactionParSite> satisfactionParSite(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String typeIntervention) {
        return service.satisfactionParSite(dateDebut, dateFin, siteId);
    }

    @GetMapping("/comparaison-periodes")
    public ComparaisonPeriodesTerrain comparaisonPeriodes(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String typeIntervention) {
        return service.comparaisonPeriodes(dateDebut, dateFin, siteId, employeId, typeIntervention);
    }
}