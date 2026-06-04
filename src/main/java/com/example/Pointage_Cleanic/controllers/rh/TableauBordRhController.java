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

@RestController
@RequestMapping("/api/tableau-bord-rh")
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