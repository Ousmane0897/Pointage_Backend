package com.example.Pointage_Cleanic.controllers.stockv2;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DecisionPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Dto.stockv2.MotifPayload;
import com.example.Pointage_Cleanic.services.stockv2.BonEntreeService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@PreAuthorize("@droits.peutUnParmi('stock.bonsEntree', 'stock.workflowValidation')")
@RestController
@RequestMapping("/api/stock/bons-entree")
@RequiredArgsConstructor
public class BonEntreeController {

    private final BonEntreeService service;

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

    /** Abandon d'un brouillon (lot 1 / 1b, D4) : remplace l'ancien DELETE, le bon est conservé. */
    @PostMapping("/{id}/abandonner")
    public BonEntreeDto abandonner(@PathVariable String id) {
        return service.abandonner(id);
    }

    /**
     * Annulation d'un bon EFFECTIF (lot 1 / 1b, D4) : remplace la suppression définitive. Le bon
     * est conservé (statut ANNULE, motif tracé) et ses mouvements sont contre-passés par des
     * mouvements inverses. Super-administrateur, motif de 10 caractères minimum.
     */
    @PostMapping("/{id}/annuler")
    public BonEntreeDto annuler(@PathVariable String id, @RequestBody(required = false) MotifPayload payload) {
        return service.annuler(id, payload == null ? null : payload.getMotif());
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
