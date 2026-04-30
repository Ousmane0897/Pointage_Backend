package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Dto.ImportEmployeRequest;
import com.example.Pointage_Cleanic.Dto.ImportEmployeResponse;
import com.example.Pointage_Cleanic.Dto.ProduitDto;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.repositories.EmployeRepository;
import com.example.Pointage_Cleanic.services.EmployeCompletService;
import com.example.Pointage_Cleanic.services.EmployeServices;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/employe-complet")
@RequiredArgsConstructor
public class EmployeCompletController {

    private final EmployeCompletService employeCompletService;
    private final EmployeCompletRepository employeCompletRepository;
    private final ObjectMapper objectMapper; // 🔥 injecté par Spring
    private final EmployeCompletRepository employeRepository;


    // ============================================
    //             CREATE ONE EMPLOYEE
    // ============================================

    @PostMapping(value = "/employe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeComplet> createEmploye(
            @RequestPart("employe") String employeJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            @RequestPart(value = "contrat", required = false) MultipartFile contrat
    ) throws IOException {

        System.out.println("RAW JSON = " + employeJson); // 🔥 LOG 1

        EmployeCompletDto dto =
                objectMapper.readValue(employeJson, EmployeCompletDto.class);

        System.out.println("DTO PRENOM = " + dto.getPrenom()); // 🔥 LOG 2
        System.out.println("DTO TELEPHONE = " + dto.getTelephone1());

        return ResponseEntity.ok(employeCompletService.create(dto, photo,contrat));
    }


    // ===========================================================
    //           CREATE MULTIPLE EMPLOYEE FROM EXCEL FILE
    // ===========================================================

    @PostMapping("/import-excel")
    public ResponseEntity<ImportEmployeResponse> importFromExcel(
            @RequestBody List<EmployeCompletDto> imported
    ) {
        System.out.println("🔥 IMPORT EXCEL APPELÉ");
        return ResponseEntity.ok(employeCompletService.importMany(imported));
    }


    /**
     * 🔍 Recherche d’employés par nom, prénom, matricule, ou email
     */
    @GetMapping("/all")
    public SearchResponse list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String q) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("nom").ascending());

        Page<EmployeComplet> pageResult;

        if (q == null || q.isBlank()) {
            pageResult = employeCompletRepository.findAll(pageable);
        } else {
            pageResult = employeCompletRepository
                    .findByPrenomContainingIgnoreCaseOrNomContainingIgnoreCaseOrMatriculeContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            q, q, q, q, pageable);
        }

        return new SearchResponse(
                pageResult.getContent(),
                pageResult.getTotalElements()
        );
    }


    /**
     * Petite classe DTO de réponse pour Angular
     */
    public record SearchResponse(List<EmployeComplet> content, long total) {} // Un record en Java est une classe immuable utilisée pour transporter des données (comme un DTO).


    @GetMapping("/{agenId}")
    public ResponseEntity<EmployeComplet> getByAgentId(String AgentId) {

        return ResponseEntity.ok(employeCompletService.getByAgentId(AgentId));
    }

    @GetMapping("/image/{agentId}")
    public ResponseEntity<byte[]> getEmployeImage(@PathVariable String agentId) {
        return employeCompletRepository.findByAgentId(agentId)
                .filter(e -> e.getPhoto() != null)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(p.getPhoto()))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/prenomNom")
    public ResponseEntity<EmployeComplet> searchEmploye(
            @RequestParam String prenom,
            @RequestParam String nom
    ) {
        EmployeComplet employe = employeCompletService.getEmploye(prenom, nom);

        return ResponseEntity.ok(employe);

    }


    @DeleteMapping("/by-agent/{agentId}")
    public void deleteEmployee(@PathVariable String agentId) {

        employeCompletService.delete(agentId);
    }

    @PutMapping(
            value = "/complet/{agentId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<EmployeComplet> update(
            @PathVariable String agentId,
            @RequestPart("employe") String employeJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) throws IOException {

        EmployeCompletDto employeCompletDto =
                objectMapper.readValue(employeJson, EmployeCompletDto.class);

        EmployeComplet updated =
                employeCompletService.update(agentId, employeCompletDto, photo);

        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{id}/contrat")
    public ResponseEntity<byte[]> getContrat(@PathVariable String id) {

        EmployeComplet emp = employeRepository.findById(id).orElseThrow();

        return ResponseEntity.ok()
                .header("Content-Type", emp.getTypeContrat())
                .header("Content-Disposition", "inline; filename=\"" + emp.getContratNom() + "\"")
                .body(emp.getContrat());
    }

    @DeleteMapping("/{id}/contrat")
    public ResponseEntity<?> deleteContrat(@PathVariable String id) {

        EmployeComplet emp = employeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Employé introuvable"));

        emp.setContrat(null);
        emp.setContratNom(null);
        emp.setTypeContrat(null);

        employeRepository.save(emp);

        return ResponseEntity.ok("Contrat supprimé");
    }


}
