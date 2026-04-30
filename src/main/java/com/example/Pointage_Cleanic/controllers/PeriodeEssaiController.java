package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.CreerDemandeValidationRequest;
import com.example.Pointage_Cleanic.Dto.DemandeValidationDto;
import com.example.Pointage_Cleanic.Dto.PeriodeEssaiDto;
import com.example.Pointage_Cleanic.Dto.ProlongerPeriodeEssaiRequest;
import com.example.Pointage_Cleanic.Dto.ValiderDemandeRequest;
import com.example.Pointage_Cleanic.services.DemandeValidationPeriodeEssaiService;
import com.example.Pointage_Cleanic.services.PeriodeEssaiService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gestion-personnel/periodes-essai")
@RequiredArgsConstructor
public class PeriodeEssaiController {

    private final PeriodeEssaiService periodeEssaiService;
    private final DemandeValidationPeriodeEssaiService demandeValidationService;

    @GetMapping
    public ResponseEntity<Page<PeriodeEssaiDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String statut
    ) {
        return ResponseEntity.ok(periodeEssaiService.list(page, size, statut));
    }

    @GetMapping("/alertes")
    public ResponseEntity<List<PeriodeEssaiDto>> getAlertes() {
        return ResponseEntity.ok(periodeEssaiService.getAlertes());
    }

    @GetMapping("/validations")
    public ResponseEntity<List<DemandeValidationDto>> listValidations(
            @RequestParam(required = false) String statut
    ) {
        return ResponseEntity.ok(demandeValidationService.list(statut));
    }

    @PutMapping("/validations/{demandeId}")
    public ResponseEntity<DemandeValidationDto> validerDemande(
            @PathVariable String demandeId,
            @RequestBody ValiderDemandeRequest request
    ) {
        return ResponseEntity.ok(
                demandeValidationService.traiter(demandeId, request, currentUser()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PeriodeEssaiDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(periodeEssaiService.getById(id));
    }

    @PutMapping("/{id}/prolonger")
    public ResponseEntity<PeriodeEssaiDto> prolonger(
            @PathVariable String id,
            @RequestBody ProlongerPeriodeEssaiRequest request
    ) {
        return ResponseEntity.ok(
                periodeEssaiService.prolonger(id, request, currentUser()));
    }

    @PostMapping("/{periodeEssaiId}/validations")
    public ResponseEntity<DemandeValidationDto> creerValidation(
            @PathVariable String periodeEssaiId,
            @RequestBody(required = false) CreerDemandeValidationRequest request
    ) {
        DemandeValidationDto created = demandeValidationService.creer(periodeEssaiId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}