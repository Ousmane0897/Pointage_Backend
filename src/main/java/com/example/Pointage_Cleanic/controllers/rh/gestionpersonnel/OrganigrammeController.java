package com.example.Pointage_Cleanic.controllers.rh.gestionpersonnel;

import com.example.Pointage_Cleanic.Dto.rh.OrganigrammeArbreDto;
import com.example.Pointage_Cleanic.Dto.rh.OrganigrammeNodeDto;
import com.example.Pointage_Cleanic.services.rh.OrganigrammeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gestion-personnel/organigramme")
@RequiredArgsConstructor
public class OrganigrammeController {

    private final OrganigrammeService organigrammeService;

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
