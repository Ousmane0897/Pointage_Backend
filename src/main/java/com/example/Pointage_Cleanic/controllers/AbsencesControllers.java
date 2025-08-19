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

    @GetMapping
    public ResponseEntity<List<Absent>> getAll() {

        List<Absent> absents = absentService.getAll();

        return ResponseEntity.ok(absents);
    }

    @PutMapping("/{codeSecret}")
    public ResponseEntity<Absent> updateAbsent(@PathVariable String codeSecret, @RequestBody Absent absentDetails) {
        Absent absent = absentService.getBycodeSecret(codeSecret);

        if (absent == null) {

            return ResponseEntity.notFound().build();
        }

        absent.setPrenom(absentDetails.getPrenom());
        absent.setNom(absentDetails.getNom());
        absent.setNumero(absentDetails.getNumero());
        absent.setDateAbsence(absentDetails.getDateAbsence());
        absent.setMotif(absentDetails.getMotif());
        absent.setJustification(absentDetails.getJustification());

        Absent absent1 = absentRepository.save(absent);

        return ResponseEntity.ok(absent1);
    }

}
