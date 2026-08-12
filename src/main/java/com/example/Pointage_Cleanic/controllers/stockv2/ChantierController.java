package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ChantierDto;
import com.example.Pointage_Cleanic.Dto.stockv2.ChantierPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DetailChantierDto;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutChantier;
import com.example.Pointage_Cleanic.services.stockv2.ChantierService;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock/chantiers")
@RequiredArgsConstructor
public class ChantierController {

    private final ChantierService service;

    @GetMapping
    public ResponseEntity<PageResponse<ChantierDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatutChantier statut,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(service.list(page, size, q, statut, siteId, dateDebut, dateFin));
    }

    @GetMapping("/actifs")
    public ResponseEntity<List<ChantierDto>> actifs() {
        return ResponseEntity.ok(service.actifs());
    }

    @GetMapping("/prochaine-reference")
    public ResponseEntity<Map<String, String>> prochaineReference() {
        return ResponseEntity.ok(Map.of("reference", service.prochaineReference()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DetailChantierDto> detail(@PathVariable String id) {
        return ResponseEntity.ok(service.detail(id));
    }

    @PostMapping
    public ResponseEntity<ChantierDto> creer(@RequestBody ChantierPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(payload));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ChantierDto> modifier(@PathVariable String id, @RequestBody ChantierPayload payload) {
        return ResponseEntity.ok(service.modifier(id, payload));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable String id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/cloture")
    public ResponseEntity<ChantierDto> cloturer(@PathVariable String id) {
        return ResponseEntity.ok(service.cloturer(id));
    }
}
