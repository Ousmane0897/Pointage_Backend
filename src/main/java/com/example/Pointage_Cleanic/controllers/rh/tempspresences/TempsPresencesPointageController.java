package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.ResumeJourneeDto;
import com.example.Pointage_Cleanic.Dto.rh.PointageCentraliseDto;
import com.example.Pointage_Cleanic.services.rh.PointageCentraliseService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Façade RH 6.2 « Temps & Présences » — vue pointage centralisé (lecture seule).
 * Réutilise {@link PointageCentraliseService} ; les données proviennent du module
 * pointage existant. Sécurisé par JWT via {@code .anyRequest().authenticated()}.
 */
@RestController
@RequestMapping("/api/temps-presences/pointages")
@RequiredArgsConstructor
public class TempsPresencesPointageController {

    private final PointageCentraliseService pointageCentraliseService;

    @GetMapping
    public ResponseEntity<PageResponse<PointageCentraliseDto>> getPointages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String q
    ) {
        if (date == null && dateDebut != null && dateFin != null) {
            return ResponseEntity.ok(PageResponse.from(
                    pointageCentraliseService.getPointagesRange(
                            dateDebut, dateFin, departement, site, statut, q, page, size)));
        }
        return ResponseEntity.ok(PageResponse.from(
                pointageCentraliseService.getPointages(date, departement, site, statut, q, page, size)));
    }

    @GetMapping("/resume")
    public ResponseEntity<ResumeJourneeDto> getResume(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(pointageCentraliseService.getResume(date));
    }
}
