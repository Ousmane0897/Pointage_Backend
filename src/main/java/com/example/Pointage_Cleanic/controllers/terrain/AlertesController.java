package com.example.Pointage_Cleanic.controllers.terrain;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.Pointage_Cleanic.Dto.terrain.AlerteTerrainDto;
import com.example.Pointage_Cleanic.Dto.terrain.ParametresEscaladeDto;
import com.example.Pointage_Cleanic.Dto.terrain.RecapitulatifQuotidien;
import com.example.Pointage_Cleanic.Enum.terrain.NiveauEscalade;
import com.example.Pointage_Cleanic.Enum.terrain.StatutAlerte;
import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteTerrain;
import com.example.Pointage_Cleanic.services.terrain.AlerteService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@PreAuthorize("@droits.peutUnParmi('terrain.*', 'dashboard')")
@RestController
@RequestMapping("/api/terrain/alertes")
@RequiredArgsConstructor
public class AlertesController {

    private final AlerteService service;

    @GetMapping
    public PageResponse<AlerteTerrainDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) TypeAlerteTerrain type,
            @RequestParam(required = false) StatutAlerte statut,
            @RequestParam(required = false) NiveauEscalade niveauActuel,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String siteId) {
        return service.list(page, size, dateDebut, dateFin, type, statut, niveauActuel, employeId, siteId);
    }

    @GetMapping("/courantes")
    public List<AlerteTerrainDto> courantes() {
        return service.courantes();
    }

    @GetMapping("/recap-quotidien")
    public RecapitulatifQuotidien recapQuotidien(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.recapQuotidien(date);
    }

    @PreAuthorize("@droits.peut('terrain.alertes')")
    @GetMapping("/parametres")
    public ParametresEscaladeDto getParametres() {
        return service.getParametres();
    }

    @PreAuthorize("@droits.peut('terrain.alertes')")
    @PutMapping("/parametres")
    public ParametresEscaladeDto updateParametres(@RequestBody ParametresEscaladeDto dto) {
        return service.updateParametres(dto);
    }

    @GetMapping("/{id}")
    public AlerteTerrainDto getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PreAuthorize("@droits.peut('terrain.alertes')")
    @PostMapping("/{id}/traiter")
    public AlerteTerrainDto traiter(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return service.traiter(id, body == null ? null : body.get("commentaire"));
    }

    @PreAuthorize("@droits.peut('terrain.alertes')")
    @PostMapping("/{id}/justifier")
    public AlerteTerrainDto justifier(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return service.justifier(id, body == null ? null : body.get("motif"));
    }

    @PreAuthorize("@droits.peut('terrain.alertes')")
    @PostMapping("/{id}/escalader")
    public AlerteTerrainDto escalader(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        return service.escalader(id, body == null ? null : body.get("motif"));
    }
}