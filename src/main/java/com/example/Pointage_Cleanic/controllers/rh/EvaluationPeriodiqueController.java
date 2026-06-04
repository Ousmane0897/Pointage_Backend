package com.example.Pointage_Cleanic.controllers.rh;

import com.example.Pointage_Cleanic.Dto.rh.AutoEvaluationRequest;
import com.example.Pointage_Cleanic.Dto.rh.EvaluationManagerRequest;
import com.example.Pointage_Cleanic.Dto.rh.EvaluationPeriodiqueDto;
import com.example.Pointage_Cleanic.Dto.rh.ValidationEvaluationRequest;
import com.example.Pointage_Cleanic.Enum.rh.StatutEvaluation;
import com.example.Pointage_Cleanic.services.rh.EvaluationPeriodiqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/evaluations")
@RequiredArgsConstructor
public class EvaluationPeriodiqueController {

    private final EvaluationPeriodiqueService evaluationService;

    @PostMapping
    public ResponseEntity<EvaluationPeriodiqueDto> create(@RequestBody EvaluationPeriodiqueDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(evaluationService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<EvaluationPeriodiqueDto>> search(
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String departement,
            @RequestParam(required = false) String periode,
            @RequestParam(required = false) StatutEvaluation statut
    ) {
        return ResponseEntity.ok(evaluationService.search(employeId, departement, periode, statut));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvaluationPeriodiqueDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(evaluationService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EvaluationPeriodiqueDto> update(
            @PathVariable String id, @RequestBody EvaluationPeriodiqueDto dto
    ) {
        return ResponseEntity.ok(evaluationService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        evaluationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/auto-evaluer")
    public ResponseEntity<EvaluationPeriodiqueDto> autoEvaluer(
            @PathVariable String id, @RequestBody AutoEvaluationRequest req
    ) {
        return ResponseEntity.ok(evaluationService.autoEvaluer(id, req));
    }

    @PutMapping("/{id}/evaluer-manager")
    public ResponseEntity<EvaluationPeriodiqueDto> evaluerManager(
            @PathVariable String id, @RequestBody EvaluationManagerRequest req
    ) {
        return ResponseEntity.ok(evaluationService.evaluerManager(id, req));
    }

    @PutMapping("/{id}/valider")
    public ResponseEntity<EvaluationPeriodiqueDto> valider(
            @PathVariable String id, @RequestBody ValidationEvaluationRequest req
    ) {
        return ResponseEntity.ok(evaluationService.valider(id, req));
    }
}