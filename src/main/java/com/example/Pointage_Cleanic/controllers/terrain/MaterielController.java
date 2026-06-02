package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AlerteMaintenance;
import com.example.Pointage_Cleanic.Enum.terrain.StatutMateriel;
import com.example.Pointage_Cleanic.Enum.terrain.TypeMateriel;
import com.example.Pointage_Cleanic.entities.terrain.EvenementMateriel;
import com.example.Pointage_Cleanic.entities.terrain.MaintenanceProgrammee;
import com.example.Pointage_Cleanic.entities.terrain.Materiel;
import com.example.Pointage_Cleanic.services.terrain.MaterielService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/terrain/materiel")
@RequiredArgsConstructor
public class MaterielController {

    private final MaterielService service;

    @GetMapping
    public PageResponse<Materiel> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TypeMateriel type,
            @RequestParam(required = false) StatutMateriel statut,
            @RequestParam(required = false) String siteAffecteId) {
        return service.list(page, size, q, type, statut, siteAffecteId);
    }

    @GetMapping("/alertes")
    public List<AlerteMaintenance> alertes() {
        return service.alertes();
    }

    @GetMapping("/maintenance-programmee")
    public List<MaintenanceProgrammee> listMaintenances(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return service.listMaintenances(dateDebut, dateFin);
    }

    @PostMapping("/maintenance-programmee")
    public ResponseEntity<MaintenanceProgrammee> creerMaintenance(@RequestBody MaintenanceProgrammee maintenance) {
        return ResponseEntity.status(201).body(service.creerMaintenance(maintenance));
    }

    @GetMapping("/{id}")
    public Materiel getById(@PathVariable String id) {
        return service.getById(id);
    }

    @PostMapping
    public ResponseEntity<Materiel> create(@RequestBody Materiel materiel) {
        return ResponseEntity.status(201).body(service.create(materiel));
    }

    @PutMapping("/{id}")
    public Materiel update(@PathVariable String id, @RequestBody Materiel materiel) {
        return service.update(id, materiel);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{materielId}/affecter")
    public Materiel affecter(@PathVariable String materielId, @RequestBody Map<String, String> body) {
        return service.affecter(materielId, body.get("siteId"), body.get("commentaire"));
    }

    @GetMapping("/{materielId}/historique")
    public List<EvenementMateriel> historique(@PathVariable String materielId) {
        return service.historique(materielId);
    }

    @PostMapping("/{materielId}/panne")
    public EvenementMateriel panne(@PathVariable String materielId, @RequestBody Map<String, String> body) {
        return service.panne(materielId, body.get("description"));
    }

    @PostMapping("/{materielId}/maintenance")
    public EvenementMateriel maintenance(@PathVariable String materielId, @RequestBody EvenementMateriel evenement) {
        return service.maintenance(materielId, evenement);
    }
}