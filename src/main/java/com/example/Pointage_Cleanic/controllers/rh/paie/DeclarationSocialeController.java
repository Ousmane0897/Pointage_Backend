package com.example.Pointage_Cleanic.controllers.rh.paie;

import com.example.Pointage_Cleanic.Dto.rh.DeclarationSocialeDto;
import com.example.Pointage_Cleanic.Enum.rh.StatutDeclaration;
import com.example.Pointage_Cleanic.Enum.rh.TypeDeclaration;
import com.example.Pointage_Cleanic.services.rh.DeclarationSocialeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/paie/declarations-sociales")
@RequiredArgsConstructor
public class DeclarationSocialeController {

    private final DeclarationSocialeService declarationSocialeService;

    @GetMapping("/ipres")
    public ResponseEntity<DeclarationSocialeDto> genererIpres(
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee
    ) {
        int[] moisAnnee = extrairePeriode(periode, mois, annee);
        DeclarationSocialeDto dto = moisAnnee[0] > 0
                ? declarationSocialeService.genererIpresMensuelle(moisAnnee[0], moisAnnee[1])
                : declarationSocialeService.genererIpresAnnuelle(moisAnnee[1]);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/css")
    public ResponseEntity<DeclarationSocialeDto> genererCss(
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee
    ) {
        int[] moisAnnee = extrairePeriode(periode, mois, annee);
        DeclarationSocialeDto dto = moisAnnee[0] > 0
                ? declarationSocialeService.genererCssMensuelle(moisAnnee[0], moisAnnee[1])
                : declarationSocialeService.genererCssAnnuelle(moisAnnee[1]);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestParam String id) {
        byte[] file = declarationSocialeService.exportPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=declaration-" + id + ".pdf")
                .body(file);
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam String id) {
        byte[] file = declarationSocialeService.exportExcel(id);
        return ResponseEntity.ok()
                .header("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header("Content-Disposition", "attachment; filename=declaration-" + id + ".xlsx")
                .body(file);
    }

    @GetMapping
    public ResponseEntity<List<DeclarationSocialeDto>> getAll(
            @RequestParam(required = false) TypeDeclaration type,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) StatutDeclaration statut
    ) {
        return ResponseEntity.ok(declarationSocialeService.getAll(type, mois, annee, statut));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeclarationSocialeDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(declarationSocialeService.getById(id));
    }

    @PutMapping("/{id}/transmettre")
    public ResponseEntity<DeclarationSocialeDto> transmettre(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String ref = body != null ? body.get("referenceExterne") : null;
        return ResponseEntity.ok(declarationSocialeService.transmettre(id, ref));
    }

    /**
     * Retourne [mois, annee] ou [0, annee] si annuelle.
     * Accepte soit ?periode=yyyy-MM, soit ?mois=&annee=.
     */
    private int[] extrairePeriode(String periode, Integer mois, Integer annee) {
        if (periode != null && !periode.isBlank()) {
            try {
                YearMonth ym = YearMonth.parse(periode);
                return new int[]{ym.getMonthValue(), ym.getYear()};
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Format période invalide (attendu yyyy-MM) : " + periode);
            }
        }
        if (annee == null) {
            throw new IllegalArgumentException("Paramètre annee ou periode requis.");
        }
        return new int[]{mois != null ? mois : 0, annee};
    }
}