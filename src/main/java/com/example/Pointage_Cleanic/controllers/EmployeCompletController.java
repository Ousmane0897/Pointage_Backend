package com.example.Pointage_Cleanic.controllers;


import com.example.Pointage_Cleanic.Dto.EmployeCompletDto;
import com.example.Pointage_Cleanic.Dto.ProduitDto;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.EmployeCompletRepository;
import com.example.Pointage_Cleanic.services.EmployeCompletService;
import com.example.Pointage_Cleanic.services.EmployeServices;
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


    // ==================================
    //            CREATE
    // ==================================
    @PostMapping(value = "/employe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeComplet> createEmploye(
            @RequestPart("employe") EmployeCompletDto dto,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {
        try {
            EmployeComplet saved = employeCompletService.create(dto, photo);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
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


    @DeleteMapping("/{matricule}")
    public void deleteEmployee(@PathVariable String matricule) {

        employeCompletService.delete(matricule);
    }

    @PutMapping(value = "/complet/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeComplet> update(
            @PathVariable String id,
            @RequestPart("employe") EmployeCompletDto employeCompletDto,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) throws IOException {

        EmployeComplet updated = employeCompletService.update(id, employeCompletDto, photo);
        return ResponseEntity.ok(updated);
    }

}
