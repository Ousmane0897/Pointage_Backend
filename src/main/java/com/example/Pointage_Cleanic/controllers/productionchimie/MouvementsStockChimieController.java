package com.example.Pointage_Cleanic.controllers.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.AjustementMpPayload;
import com.example.Pointage_Cleanic.Dto.productionchimie.MouvementStockChimieDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.ReceptionMpPayload;
import com.example.Pointage_Cleanic.Enum.TypeMouvementChimie;
import com.example.Pointage_Cleanic.services.productionchimie.MouvementStockChimieService;
import com.example.Pointage_Cleanic.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/production-chimie/stock-chimie/mouvements")
@RequiredArgsConstructor
public class MouvementsStockChimieController {

    private final MouvementStockChimieService service;

    @GetMapping
    public ResponseEntity<PageResponse<MouvementStockChimieDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String matierePremiereId,
            @RequestParam(required = false) TypeMouvementChimie type,
            @RequestParam(required = false) String ordreFabricationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin
    ) {
        return ResponseEntity.ok(service.list(page, size, matierePremiereId, type, ordreFabricationId, dateDebut, dateFin));
    }

    @PostMapping("/reception")
    public ResponseEntity<MouvementStockChimieDto> reception(@Valid @RequestBody ReceptionMpPayload payload) {
        return ResponseEntity.status(201).body(service.createReception(payload));
    }

    @PostMapping("/ajustement")
    public ResponseEntity<MouvementStockChimieDto> ajustement(@Valid @RequestBody AjustementMpPayload payload) {
        return ResponseEntity.status(201).body(service.createAjustement(payload));
    }
}