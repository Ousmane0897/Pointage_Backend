package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Dto.OrganigrammeArbreDto;
import com.example.Pointage_Cleanic.Dto.OrganigrammeNodeDto;
import com.example.Pointage_Cleanic.services.OrganigrammeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organigramme")
@RequiredArgsConstructor
public class OrganigrammeController {

    private final OrganigrammeService organigrammeService;

    @GetMapping
    public ResponseEntity<List<OrganigrammeNodeDto>> getOrganigramme(
            @RequestParam(defaultValue = "") String departement
    ) {
        return ResponseEntity.ok(organigrammeService.getOrganigramme(departement));
    }

    @GetMapping("/arbre")
    public ResponseEntity<List<OrganigrammeArbreDto>> getArbre() {
        return ResponseEntity.ok(organigrammeService.getArbre());
    }
}
