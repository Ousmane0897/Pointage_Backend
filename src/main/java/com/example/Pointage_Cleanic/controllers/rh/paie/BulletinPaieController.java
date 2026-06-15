package com.example.Pointage_Cleanic.controllers.rh.paie;

import com.example.Pointage_Cleanic.Dto.rh.BulletinPaieDto;
import com.example.Pointage_Cleanic.Dto.rh.CalculBulletinRequest;
import com.example.Pointage_Cleanic.Dto.rh.ValiderBulletinRequest;
import com.example.Pointage_Cleanic.Enum.rh.StatutBulletin;
import com.example.Pointage_Cleanic.services.rh.BulletinPaieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/paie/bulletins")
@RequiredArgsConstructor
public class BulletinPaieController {

    private final BulletinPaieService bulletinPaieService;

    @PostMapping("/calculer")
    public ResponseEntity<BulletinPaieDto> calculer(@RequestBody CalculBulletinRequest request) {
        BulletinPaieDto dto = bulletinPaieService.calculerEtSauvegarder(
                request.getEmployeId(), request.getCategorieCode(),
                request.getMois(), request.getAnnee());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/historique")
    public ResponseEntity<List<BulletinPaieDto>> getHistorique(
            @RequestParam String employeId,
            @RequestParam int annee
    ) {
        return ResponseEntity.ok(bulletinPaieService.getHistorique(employeId, annee));
    }

    @GetMapping
    public ResponseEntity<List<BulletinPaieDto>> getAll(
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) StatutBulletin statut,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(
                bulletinPaieService.getAll(employeId, departement, mois, annee, statut, q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BulletinPaieDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(bulletinPaieService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BulletinPaieDto> update(
            @PathVariable String id, @RequestBody BulletinPaieDto dto
    ) {
        return ResponseEntity.ok(bulletinPaieService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        bulletinPaieService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/valider")
    public ResponseEntity<BulletinPaieDto> valider(
            @PathVariable String id,
            @RequestBody(required = false) ValiderBulletinRequest request
    ) {
        return ResponseEntity.ok(bulletinPaieService.valider(id, request));
    }

    @PutMapping("/{id}/payer")
    public ResponseEntity<BulletinPaieDto> payer(@PathVariable String id) {
        return ResponseEntity.ok(bulletinPaieService.marquerPaye(id));
    }

    @PutMapping("/{id}/annuler")
    public ResponseEntity<BulletinPaieDto> annuler(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String commentaire = body != null ? body.get("commentaire") : null;
        return ResponseEntity.ok(bulletinPaieService.annuler(id, commentaire));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable String id) {
        byte[] file = bulletinPaieService.exportPdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=bulletin-" + id + ".pdf")
                .body(file);
    }
}