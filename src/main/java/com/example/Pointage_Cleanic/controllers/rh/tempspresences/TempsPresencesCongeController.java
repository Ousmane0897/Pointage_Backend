package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.DemandeCongeDto;
import com.example.Pointage_Cleanic.Dto.rh.SoldeCongeDto;
import com.example.Pointage_Cleanic.services.rh.DemandeCongeService;
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
 * Façade RH 6.2 « Temps & Présences » — soldes + demandes de congés + workflow.
 * Le décideur (decideurId/decideurNom) est résolu depuis le JWT via
 * {@link CurrentUserProvider}. Réutilise {@link DemandeCongeService}.
 */
@RestController
@RequestMapping("/api/temps-presences/conges")
@RequiredArgsConstructor
public class TempsPresencesCongeController {

    private final DemandeCongeService demandeCongeService;
    private final CurrentUserProvider currentUserProvider;

    // --- Soldes (tableau brut / objet seul) ---

    @GetMapping("/soldes")
    public ResponseEntity<List<SoldeCongeDto>> getSoldes(
            @RequestParam(required = false) String employeId) {
        if (employeId != null && !employeId.isBlank()) {
            return ResponseEntity.ok(List.of(demandeCongeService.getSolde(employeId)));
        }
        return ResponseEntity.ok(demandeCongeService.getSoldes());
    }

    @GetMapping("/soldes/{employeId}")
    public ResponseEntity<SoldeCongeDto> getSolde(@PathVariable String employeId) {
        return ResponseEntity.ok(demandeCongeService.getSolde(employeId));
    }

    // --- Demandes (CRUD paginé + workflow) ---

    @GetMapping("/demandes")
    public ResponseEntity<PageResponse<DemandeCongeDto>> searchDemandes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String q
    ) {
        return ResponseEntity.ok(PageResponse.from(demandeCongeService.searchDemandes(
                employeId, departement, statut, type, dateDebut, dateFin, q, page, size)));
    }

    @GetMapping("/demandes/{id}")
    public ResponseEntity<DemandeCongeDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(demandeCongeService.getById(id));
    }

    @GetMapping("/demandes/employe/{employeId}")
    public ResponseEntity<List<DemandeCongeDto>> getByEmployeId(@PathVariable String employeId) {
        return ResponseEntity.ok(demandeCongeService.getByEmployeId(employeId));
    }

    @PostMapping("/demandes")
    public ResponseEntity<DemandeCongeDto> create(@RequestBody DemandeCongeDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(demandeCongeService.create(dto));
    }

    @PutMapping("/demandes/{id}")
    public ResponseEntity<DemandeCongeDto> update(@PathVariable String id, @RequestBody DemandeCongeDto dto) {
        return ResponseEntity.ok(demandeCongeService.update(id, dto));
    }

    @DeleteMapping("/demandes/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        demandeCongeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/demandes/{id}/approuver")
    public ResponseEntity<DemandeCongeDto> approuver(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String commentaire = body != null ? body.get("commentaire") : null;
        return ResponseEntity.ok(demandeCongeService.approuver(
                id, currentUserProvider.currentUserId(), currentUserProvider.currentUserNom(), commentaire));
    }

    @PostMapping("/demandes/{id}/refuser")
    public ResponseEntity<DemandeCongeDto> refuser(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String motif = body != null ? (body.get("motif") != null ? body.get("motif") : body.get("commentaire")) : null;
        return ResponseEntity.ok(demandeCongeService.refuser(
                id, currentUserProvider.currentUserId(), currentUserProvider.currentUserNom(), motif));
    }
}
