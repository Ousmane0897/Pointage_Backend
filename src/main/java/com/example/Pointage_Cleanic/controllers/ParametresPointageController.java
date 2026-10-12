package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.pointage.ParametresPointageDto;
import com.example.Pointage_Cleanic.services.pointage.ParametresPointageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Paramètres du pointage kiosque. ⚠ Authentifié par une règle explicite de
 * {@code SecurityConfig}, placée avant {@code GET /api/pointages/{codeSecret}} (public) :
 * sans elle, la lecture serait ouverte à tous. Le PUT est réservé RH / SUPERADMIN (service).
 */
@RestController
@RequestMapping("/api/pointages/parametres")
@RequiredArgsConstructor
public class ParametresPointageController {

    private final ParametresPointageService service;

    @GetMapping
    public ParametresPointageDto get() {
        return service.getParametres();
    }

    @PutMapping
    public ParametresPointageDto update(@RequestBody ParametresPointageDto dto) {
        return service.updateParametres(dto);
    }
}
