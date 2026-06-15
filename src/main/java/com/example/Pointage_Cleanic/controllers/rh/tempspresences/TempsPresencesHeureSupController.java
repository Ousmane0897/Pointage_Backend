package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.HeureSupplementaireDto;
import com.example.Pointage_Cleanic.services.rh.HeureSupplementaireService;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Façade RH 6.2 « Temps & Présences » — heures supplémentaires + workflow validation.
 * Le décideur est résolu depuis le JWT via {@link CurrentUserProvider}.
 * Réutilise {@link HeureSupplementaireService}.
 */
@RestController
@RequestMapping("/api/temps-presences/heures-supplementaires")
@RequiredArgsConstructor
public class TempsPresencesHeureSupController {

    private final HeureSupplementaireService heureSupplementaireService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    public ResponseEntity<PageResponse<HeureSupplementaireDto>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String typeMajoration,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(PageResponse.from(heureSupplementaireService.search(
                employeId, departement, statut, typeMajoration, dateDebut, dateFin, q, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HeureSupplementaireDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(heureSupplementaireService.getById(id));
    }

    @GetMapping("/employe/{employeId}")
    public ResponseEntity<List<HeureSupplementaireDto>> getByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(heureSupplementaireService.getByEmployeId(employeId));
    }

    @PostMapping
    public ResponseEntity<HeureSupplementaireDto> create(@RequestBody HeureSupplementaireDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(heureSupplementaireService.create(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<HeureSupplementaireDto> update(@PathVariable String id, @RequestBody HeureSupplementaireDto dto) {
        return ResponseEntity.ok(heureSupplementaireService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        heureSupplementaireService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/valider")
    public ResponseEntity<HeureSupplementaireDto> valider(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String commentaire = body != null ? body.get("commentaire") : null;
        return ResponseEntity.ok(heureSupplementaireService.valider(
                id, currentUserProvider.currentUserId(), currentUserProvider.currentUserNom(), commentaire));
    }

    @PostMapping("/{id}/refuser")
    public ResponseEntity<HeureSupplementaireDto> refuser(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String motif = body != null ? (body.get("motif") != null ? body.get("motif") : body.get("commentaire")) : null;
        return ResponseEntity.ok(heureSupplementaireService.refuser(
                id, currentUserProvider.currentUserId(), currentUserProvider.currentUserNom(), motif));
    }
}
