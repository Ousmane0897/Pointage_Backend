package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.PointageCentraliseDto;
import com.example.Pointage_Cleanic.Dto.ResumeJourneeDto;
import com.example.Pointage_Cleanic.services.PointageCentraliseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/pointages-centralises")
@RequiredArgsConstructor
public class PointageCentraliseController {

    private final PointageCentraliseService pointageCentraliseService;

    @GetMapping
    public ResponseEntity<Page<PointageCentraliseDto>> getPointages(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "") String departement,
            @RequestParam(defaultValue = "") String site,
            @RequestParam(defaultValue = "") String statut,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                pointageCentraliseService.getPointages(date, departement, site, statut, q, page, size));
    }

    @GetMapping("/resume")
    public ResponseEntity<ResumeJourneeDto> getResume(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(pointageCentraliseService.getResume(date));
    }
}