package com.example.Pointage_Cleanic.controllers.rh.paie;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.Pointage_Cleanic.Dto.rh.ParametresPaieDto;
import com.example.Pointage_Cleanic.services.rh.ParametresPaieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@PreAuthorize("@droits.peut('rh.calculBulletin')")
@RestController
@RequestMapping("/api/paie/parametres")
@RequiredArgsConstructor
public class ParametresPaieController {

    private final ParametresPaieService parametresPaieService;

    @PreAuthorize("@droits.peutUnParmi('rh.grilleSalariale', 'rh.calculBulletin', 'rh.declarations')")
    @GetMapping
    public ResponseEntity<ParametresPaieDto> get() {
        return ResponseEntity.ok(parametresPaieService.get());
    }

    @PutMapping
    public ResponseEntity<ParametresPaieDto> update(
            @RequestBody ParametresPaieDto dto,
            @RequestParam(required = false) String modifieParId,
            @RequestParam(required = false) String modifieParNom
    ) {
        return ResponseEntity.ok(parametresPaieService.update(dto, modifieParId, modifieParNom));
    }
}