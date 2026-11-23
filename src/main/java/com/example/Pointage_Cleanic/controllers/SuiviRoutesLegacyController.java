package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.security.AppelRouteLegacy;
import com.example.Pointage_Cleanic.security.SuiviRoutesLegacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Compteurs des appels aux routes en sursis (lot 0 sécurité, C7). Lecture seule, SUPERADMIN. */
@PreAuthorize("@droits.estSuperAdmin()")
@RestController
@RequiredArgsConstructor
public class SuiviRoutesLegacyController {

    private final SuiviRoutesLegacyService service;

    @GetMapping("/api/securite/routes-legacy")
    public List<AppelRouteLegacy> lister() {
        return service.lister();
    }
}
