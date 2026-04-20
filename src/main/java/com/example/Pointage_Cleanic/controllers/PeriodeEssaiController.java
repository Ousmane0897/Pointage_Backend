package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.PeriodeEssaiDto;
import com.example.Pointage_Cleanic.services.PeriodeEssaiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/periodes-essai")
@RequiredArgsConstructor
public class PeriodeEssaiController {

    private final PeriodeEssaiService periodeEssaiService;

    @GetMapping("/alertes")
    public ResponseEntity<List<PeriodeEssaiDto>> getAlertes() {
        return ResponseEntity.ok(periodeEssaiService.getAlertes());
    }

    @PutMapping("/{id}/titulariser")
    public ResponseEntity<PeriodeEssaiDto> titulariser(@PathVariable String id) {
        return ResponseEntity.ok(periodeEssaiService.titulariser(id));
    }
}