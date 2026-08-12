package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComptagePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.InventaireDto;
import com.example.Pointage_Cleanic.Dto.stockv2.InventairePlanifPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.example.Pointage_Cleanic.Dto.stockv2.MotifPayload;
import com.example.Pointage_Cleanic.services.stockv2.InventaireService;
import com.example.Pointage_Cleanic.services.stockv2.SuppressionDefinitiveService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stock/inventaires")
@RequiredArgsConstructor
public class InventaireController {

    private final InventaireService service;
    private final SuppressionDefinitiveService suppressionService;

    @GetMapping
    public ResponseEntity<PageResponse<InventaireDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatutInventaire statut,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin
    ) {
        return ResponseEntity.ok(service.list(page, size, q, statut, siteId, dateDebut, dateFin));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventaireDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<InventaireDto> create(@RequestBody InventairePlanifPayload payload) {
        return ResponseEntity.ok(service.create(payload));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventaireDto> update(@PathVariable String id, @RequestBody InventairePlanifPayload payload) {
        return ResponseEntity.ok(service.update(id, payload));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Suppression d'un inventaire quel que soit son statut, <b>super-administrateur uniquement</b> :
     * les écarts appliqués à la clôture sont contre-passés et l'opération est journalisée.
     * POST (et non DELETE) car un corps de requête est requis (motif).
     */
    @PostMapping("/{id}/suppression-definitive")
    public ResponseEntity<Void> supprimerDefinitivement(@PathVariable String id,
                                                        @RequestBody(required = false) MotifPayload payload) {
        suppressionService.supprimerInventaire(id, payload == null ? null : payload.getMotif());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/comptage")
    public ResponseEntity<InventaireDto> demarrerComptage(@PathVariable String id) {
        return ResponseEntity.ok(service.demarrerComptage(id));
    }

    @PutMapping("/{id}/comptage")
    public ResponseEntity<InventaireDto> enregistrerComptage(@PathVariable String id, @RequestBody ComptagePayload payload) {
        return ResponseEntity.ok(service.enregistrerComptage(id, payload));
    }

    @PostMapping("/{id}/validation")
    public ResponseEntity<InventaireDto> valider(@PathVariable String id) {
        return ResponseEntity.ok(service.valider(id));
    }

    @PostMapping("/{id}/cloture")
    public ResponseEntity<InventaireDto> cloturer(@PathVariable String id) {
        return ResponseEntity.ok(service.cloturer(id));
    }
}
