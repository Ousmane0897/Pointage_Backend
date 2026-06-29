package com.example.Pointage_Cleanic.controllers.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ConsommationPlafondDto;
import com.example.Pointage_Cleanic.Dto.stockv2.PlafondDto;
import com.example.Pointage_Cleanic.Dto.stockv2.PlafondPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.GranularitePlafond;
import com.example.Pointage_Cleanic.services.stockv2.PlafondService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/api/stock/plafonds")
@RequiredArgsConstructor
public class PlafondController {

    private final PlafondService service;

    @GetMapping
    public ResponseEntity<PageResponse<PlafondDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) GranularitePlafond granularite,
            @RequestParam(required = false) Boolean actif) {
        return ResponseEntity.ok(service.list(page, size, q, siteId, granularite, actif));
    }

    @GetMapping("/consommation")
    public ResponseEntity<List<ConsommationPlafondDto>> consommation(
            @RequestParam String mois,
            @RequestParam(required = false) String siteId) {
        return ResponseEntity.ok(service.consommation(mois, siteId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlafondDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<PlafondDto> creer(@RequestBody PlafondPayload payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.creer(payload));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PlafondDto> modifier(@PathVariable String id, @RequestBody PlafondPayload payload) {
        return ResponseEntity.ok(service.modifier(id, payload));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable String id) {
        service.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
