package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Dto.ImportEmployeRequest;
import com.example.Pointage_Cleanic.Dto.ImportEmployeResponse;
import com.example.Pointage_Cleanic.Dto.ProduitDto;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
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


    // ============================================
    //             CREATE ONE EMPLOYEE
    // ============================================

    @PostMapping(value = "/employe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeComplet> createEmploye(
            @RequestPart("employe") String employeJson,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) throws IOException {

        System.out.println("RAW JSON = " + employeJson); // 🔥 LOG 1

        EmployeCompletDto dto =
                objectMapper.readValue(employeJson, EmployeCompletDto.class);

        System.out.println("DTO PRENOM = " + dto.getPrenom()); // 🔥 LOG 2
        System.out.println("DTO TELEPHONE = " + dto.getTelephone1());

        return ResponseEntity.ok(employeCompletService.create(dto, photo));
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



    // Retourne une page de produit contenant 20 produits
    @GetMapping("/all")
    public Page<EmployeComplet> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q) {

        // Si aucun mot-clé de recherche : on renvoie la page complète
        if (q == null || q.isBlank()) {
            Page<EmployeComplet> all = employeCompletRepository.findAll(PageRequest.of(page, size));
            return new PageImpl<>(all.getContent(), all.getPageable(), all.getTotalElements());
        }

        // Sinon on cherche le produit par code
        return employeCompletRepository.findByPrenom(q)
                .map(p -> new PageImpl<>(List.of(p), PageRequest.of(page, size), 1))
                .orElseGet(() -> {
                    Page<EmployeComplet> all = employeCompletRepository.findAll(PageRequest.of(page, size));
                    return new PageImpl<>(all.getContent(), all.getPageable(), all.getTotalElements());
                });
    }


    /**
     * 🔍 Recherche d’employés par nom, prénom, matricule, ou email
     */
    @GetMapping("/search")
    public SearchResponse searchEmployes(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("nom").ascending());

        Page<EmployeComplet> employePage;

        if (query == null || query.trim().isEmpty()) {
            employePage = employeCompletRepository.findAll(pageable);
        } else {
            // 🔍 recherche insensible à la casse sur plusieurs champs
            employePage = employeCompletRepository
                    .findByPrenomContainingIgnoreCaseOrNomContainingIgnoreCaseOrMatriculeContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            query, query, query, query, pageable);
        }

        return new SearchResponse(employePage.getContent(), employePage.getTotalElements());
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


}
