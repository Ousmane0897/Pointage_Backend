package com.example.Pointage_Cleanic.controllers.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ParametresProductionChimieDto;
import com.example.Pointage_Cleanic.services.productionchimie.ParametresProductionChimieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Paramétrage global du module Production Chimie (tolérance de contrôle du total, etc.). */
@RestController
@RequestMapping("/api/production-chimie/parametres")
@RequiredArgsConstructor
public class ParametresProductionChimieController {

    private final ParametresProductionChimieService service;

    @GetMapping
    public ResponseEntity<ParametresProductionChimieDto> get() {
        return ResponseEntity.ok(service.getParametres());
    }

    @PutMapping
    public ResponseEntity<ParametresProductionChimieDto> update(
            @Valid @RequestBody ParametresProductionChimieDto dto) {
        return ResponseEntity.ok(service.updateParametres(dto));
    }
}
