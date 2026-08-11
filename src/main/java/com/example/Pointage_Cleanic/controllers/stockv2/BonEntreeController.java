package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DecisionPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Dto.stockv2.MotifPayload;
import com.example.Pointage_Cleanic.services.stockv2.BonEntreeService;
import com.example.Pointage_Cleanic.services.stockv2.SuppressionDefinitiveService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/stock/bons-entree")
@RequiredArgsConstructor
public class BonEntreeController {

    private final BonEntreeService service;
    private final SuppressionDefinitiveService suppressionService;

    @GetMapping
    public ResponseEntity<PageResponse<BonEntreeDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatutBon statut,
            @RequestParam(required = false) TypeEntree type,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.list(page, size, q, statut, type, siteId, dateDebut, dateFin));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BonEntreeDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<BonEntreeDto> creer(@RequestBody BonEntreePayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(payload));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BonEntreeDto> modifier(@PathVariable String id, @RequestBody BonEntreePayload payload) {
        return ResponseEntity.ok(service.modifier(id, payload));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable String id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Suppression d'un bon quel que soit son statut, <b>super-administrateur uniquement</b> : les
     * mouvements d'entrée générés à la validation sont contre-passés (422 si la marchandise a déjà
     * été consommée) et l'opération est journalisée.
     */
    @PostMapping("/{id}/suppression-definitive")
    public ResponseEntity<Void> supprimerDefinitivement(@PathVariable String id,
                                                        @RequestBody(required = false) MotifPayload payload) {
        suppressionService.supprimerBonEntree(id, payload == null ? null : payload.getMotif());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/soumettre")
    public ResponseEntity<BonEntreeDto> soumettre(@PathVariable String id) {
        return ResponseEntity.ok(service.soumettre(id));
    }

    @PostMapping("/{id}/valider")
    public ResponseEntity<BonEntreeDto> valider(@PathVariable String id,
                                                @RequestBody(required = false) DecisionPayload decision) {
        return ResponseEntity.ok(service.valider(id, decision));
    }

    @PostMapping("/{id}/refuser")
    public ResponseEntity<BonEntreeDto> refuser(@PathVariable String id,
                                                @RequestBody(required = false) DecisionPayload decision) {
        return ResponseEntity.ok(service.refuser(id, decision));
    }
}
