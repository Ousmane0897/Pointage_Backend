package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.KpiRhDto;
import com.example.Pointage_Cleanic.services.rh.TableauBordRhService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * KPI du tableau de bord RH (6.4).
 *
 * <p>⚠ Mappé auparavant sur {@code /api/tableau-bord-rh}, que <b>rien n'appelait</b> : le front
 * interroge {@code /api/developpement-rh/tableau-bord} ({@code tableau-bord-rh.service.ts}), URL
 * alors occupée par {@code BesoinFormationController}, dont le {@code GET} renvoyait une liste de
 * besoins de formation en lieu et place des KPI.
 */
@RestController
@RequestMapping("/api/developpement-rh/tableau-bord")
@RequiredArgsConstructor
public class TableauBordRhController {

    private final TableauBordRhService tableauBordRhService;

    @GetMapping
    public ResponseEntity<KpiRhDto> getKpis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String site
    ) {
        return ResponseEntity.ok(
                tableauBordRhService.calculer(dateDebut, dateFin, departement, site));
    }
}