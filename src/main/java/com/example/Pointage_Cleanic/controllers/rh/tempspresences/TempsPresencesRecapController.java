package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.RecapitulatifMensuelDto;
import com.example.Pointage_Cleanic.services.rh.RecapitulatifMensuelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 * Façade RH 6.2 « Temps & Présences » — récapitulatif mensuel agrégé (lecture seule,
 * tableau brut). Combine pointages, congés et heures supplémentaires via
 * {@link RecapitulatifMensuelService#getRecapitulatifDetaille}.
 */
@RestController
@RequestMapping("/api/temps-presences/recapitulatif")
@RequiredArgsConstructor
public class TempsPresencesRecapController {

    private final RecapitulatifMensuelService recapitulatifMensuelService;

    @GetMapping
    public ResponseEntity<List<RecapitulatifMensuelDto>> getRecapitulatif(
            @RequestParam int mois,
            @RequestParam int annee,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String site,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(
                recapitulatifMensuelService.getRecapitulatifDetaille(mois, annee, departement, site, q));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam int mois,
            @RequestParam int annee,
            @RequestParam(defaultValue = "") String departement
    ) throws IOException {
        byte[] file = recapitulatifMensuelService.exportExcel(mois, annee, departement);
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=recapitulatif-" + mois + "-" + annee + ".xlsx")
                .body(file);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam int mois,
            @RequestParam int annee,
            @RequestParam(defaultValue = "") String departement
    ) throws IOException {
        byte[] file = recapitulatifMensuelService.exportPdf(mois, annee, departement);
        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=recapitulatif-" + mois + "-" + annee + ".pdf")
                .body(file);
    }
}
