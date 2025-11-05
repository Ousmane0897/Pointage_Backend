package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Absent;
import com.example.Pointage_Cleanic.repositories.AbsentRepository;
import com.example.Pointage_Cleanic.services.AbsentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/absences")
@RequiredArgsConstructor
public class AbsencesControllers {

    private final AbsentService absentService;
    private final AbsentRepository absentRepository;

    // 🔹 Historique (enregistré en base)
    @GetMapping
    public ResponseEntity<List<Absent>> getAll() {
        List<Absent> absents = absentService.getAll();
        return ResponseEntity.ok(absents);
    }

    // 🔹 Temps réel (non enregistrés)
    @GetMapping("/temps-reel")
    public ResponseEntity<List<Absent>> getDynamicAbsences() {
        List<Absent> absentsDynamiques = absentService.findAbsencesDynamiques();
        return ResponseEntity.ok(absentsDynamiques);
    }

    // 🔹 Mise à jour (motif, justification…)
    @PutMapping("/{codeSecret}")
    public ResponseEntity<Absent> updateAbsent(@PathVariable String codeSecret, @RequestBody Absent absentDetails) {
        Absent absent = absentService.getBycodeSecret(codeSecret);
        if (absent == null) return ResponseEntity.notFound().build();

        absent.setPrenom(absentDetails.getPrenom());
        absent.setNom(absentDetails.getNom());
        absent.setNumero(absentDetails.getNumero());
        absent.setDateAbsence(absentDetails.getDateAbsence());
        absent.setMotif(absentDetails.getMotif());
        absent.setJustification(absentDetails.getJustification());

        Absent updated = absentRepository.save(absent);
        return ResponseEntity.ok(updated);
    }
}
