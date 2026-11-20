package com.example.Pointage_Cleanic.controllers.rh.gestionpersonnel;

import org.springframework.security.access.prepost.PreAuthorize;

import com.example.Pointage_Cleanic.Dto.rh.OrganigrammeArbreDto;
import com.example.Pointage_Cleanic.Dto.rh.OrganigrammeNodeDto;
import com.example.Pointage_Cleanic.services.rh.OrganigrammeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("@droits.peut('rh.organigramme')")
@RestController
@RequestMapping("/api/gestion-personnel/organigramme")
@RequiredArgsConstructor
public class OrganigrammeController {

    private final OrganigrammeService organigrammeService;

    @PreAuthorize("@droits.peutUnParmi('rh.*')")
    @GetMapping("/departements")
    public ResponseEntity<List<OrganigrammeNodeDto>> getOrganigramme(
            @RequestParam(defaultValue = "") String departement
    ) {
        return ResponseEntity.ok(organigrammeService.getOrganigramme(departement));
    }

    @GetMapping
    public ResponseEntity<List<OrganigrammeArbreDto>> getArbre() {
        return ResponseEntity.ok(organigrammeService.getArbre());
    }
}
