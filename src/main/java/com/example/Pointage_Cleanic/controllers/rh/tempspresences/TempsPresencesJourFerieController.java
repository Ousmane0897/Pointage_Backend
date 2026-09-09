package com.example.Pointage_Cleanic.controllers.rh.tempspresences;

import com.example.Pointage_Cleanic.Dto.rh.JourFerieDto;
import com.example.Pointage_Cleanic.services.rh.JourFerieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Référentiel RH des jours fériés (module 6.2 « Temps &amp; Présences »).
 *
 * <p>⚠ Route <b>neuve</b> : l'ancien {@code /api/ferie}, supprimé au commit {@code a31c2f5},
 * n'est pas ressuscité — le calendrier appartient au module Temps &amp; Présences, aux côtés
 * du récapitulatif et des congés qui le consomment.
 *
 * <p>Habilitations : lecture ouverte à tout compte authentifié (les écrans de solde et le
 * récapitulatif en dépendent), écriture restreinte RH / super-admin — la garde est portée par
 * {@link JourFerieService}, pas par une annotation.
 */
@RestController
@RequestMapping("/api/temps-presences/jours-feries")
@RequiredArgsConstructor
public class TempsPresencesJourFerieController {

    private final JourFerieService jourFerieService;

    /** Fériés d'une année civile ; {@code annee} omise ⇒ année courante. */
    @GetMapping
    public ResponseEntity<List<JourFerieDto>> lister(@RequestParam(required = false) Integer annee) {
        int cible = annee != null ? annee : LocalDate.now().getYear();
        return ResponseEntity.ok(jourFerieService.getParAnnee(cible));
    }

    @PostMapping
    public ResponseEntity<JourFerieDto> creer(@Valid @RequestBody JourFerieDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jourFerieService.creer(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JourFerieDto> modifier(@PathVariable String id,
                                                 @Valid @RequestBody JourFerieDto dto) {
        return ResponseEntity.ok(jourFerieService.modifier(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable String id) {
        jourFerieService.supprimer(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Recopie les fériés à date fixe d'une année sur l'autre. Renvoie les seuls fériés
     * <b>créés</b> — une liste vide signifie que tout était déjà en place, pas un échec.
     */
    @PostMapping("/dupliquer")
    public ResponseEntity<List<JourFerieDto>> dupliquer(@RequestParam int anneeSource,
                                                        @RequestParam int anneeCible) {
        return ResponseEntity.ok(jourFerieService.dupliquerAnnee(anneeSource, anneeCible));
    }
}
