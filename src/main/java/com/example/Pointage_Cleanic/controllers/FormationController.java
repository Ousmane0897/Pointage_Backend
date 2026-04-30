package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.EvaluationFormationDto;
import com.example.Pointage_Cleanic.Dto.FormationDto;
import com.example.Pointage_Cleanic.Dto.ParticipationFormationDto;
import com.example.Pointage_Cleanic.Dto.SessionFormationDto;
import com.example.Pointage_Cleanic.services.FormationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/formations")
@RequiredArgsConstructor
public class FormationController {

    private final FormationService formationService;

    // ============ Formations ============

    @PostMapping
    public ResponseEntity<FormationDto> create(@RequestBody FormationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(formationService.create(dto));
    }

    @GetMapping
    public ResponseEntity<List<FormationDto>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean actif
    ) {
        return ResponseEntity.ok(formationService.search(q, actif));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormationDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(formationService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FormationDto> update(@PathVariable String id, @RequestBody FormationDto dto) {
        return ResponseEntity.ok(formationService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        formationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ============ Sessions ============

    @GetMapping("/{formationId}/sessions")
    public ResponseEntity<List<SessionFormationDto>> listSessions(@PathVariable String formationId) {
        return ResponseEntity.ok(formationService.listSessions(formationId));
    }

    @PostMapping("/{formationId}/sessions")
    public ResponseEntity<SessionFormationDto> addSession(
            @PathVariable String formationId, @RequestBody SessionFormationDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formationService.addSession(formationId, dto));
    }

    @PutMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionFormationDto> updateSession(
            @PathVariable String sessionId, @RequestBody SessionFormationDto dto
    ) {
        return ResponseEntity.ok(formationService.updateSession(sessionId, dto));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable String sessionId) {
        formationService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    // ============ Participants ============

    @GetMapping("/sessions/{sessionId}/participants")
    public ResponseEntity<List<ParticipationFormationDto>> listParticipants(
            @PathVariable String sessionId
    ) {
        return ResponseEntity.ok(formationService.listParticipants(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/participants")
    public ResponseEntity<ParticipationFormationDto> addParticipant(
            @PathVariable String sessionId, @RequestBody ParticipationFormationDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formationService.addParticipant(sessionId, dto));
    }

    @PutMapping("/participations/{participationId}/presence")
    public ResponseEntity<ParticipationFormationDto> marquerPresence(
            @PathVariable String participationId, @RequestBody Map<String, Boolean> body
    ) {
        boolean present = Boolean.TRUE.equals(body.get("present"));
        return ResponseEntity.ok(formationService.marquerPresence(participationId, present));
    }

    @PutMapping("/participations/{participationId}/completion")
    public ResponseEntity<ParticipationFormationDto> marquerCompletee(
            @PathVariable String participationId, @RequestBody Map<String, Boolean> body
    ) {
        boolean completee = Boolean.TRUE.equals(body.get("completee"));
        return ResponseEntity.ok(formationService.marquerCompletee(participationId, completee));
    }

    // ============ Évaluations à chaud ============

    @GetMapping("/sessions/{sessionId}/evaluations")
    public ResponseEntity<List<EvaluationFormationDto>> listEvaluations(
            @PathVariable String sessionId
    ) {
        return ResponseEntity.ok(formationService.listEvaluations(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/evaluations")
    public ResponseEntity<EvaluationFormationDto> addEvaluation(
            @PathVariable String sessionId, @RequestBody EvaluationFormationDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formationService.addEvaluation(sessionId, dto));
    }
}