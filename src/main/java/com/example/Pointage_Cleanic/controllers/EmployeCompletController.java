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


    @PostMapping(value = "/employe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) // Donc il faut dire à Spring Boot que la requête est multipart/form-data.Sinon le JSON ne sera pas désérialisé correctement.
    public ResponseEntity<EmployeComplet> createEmployeComplet(
            @RequestPart("employe") EmployeCompletDto employeDto,
            @RequestParam(value = "photo", required = false) MultipartFile photo
    ) {
        try {
            byte[] photoData = photo != null ? photo.getBytes() : null;

            EmployeComplet employe = EmployeComplet.builder()
                    .agentId(employeDto.getAgentId())
                    .matricule(employeDto.getMatricule())
                    .prenom(employeDto.getPrenom())
                    .nom(employeDto.getNom())
                    .sexe(employeDto.getSexe())
                    .dateNaissance(employeDto.getDateNaissance())
                    .lieuNaissance(employeDto.getLieuNaissance())
                    .nationalite(employeDto.getNationalite())
                    .etatCivil(employeDto.getEtatCivil())
                    .adresse(employeDto.getAdresse())
                    .ville(employeDto.getVille())
                    .telephone1(employeDto.getTelephone1())
                    .telephone2(employeDto.getTelephone2())
                    .email(employeDto.getEmail())
                    .contactUrgence(employeDto.getContactUrgence())
                    .lienDeParenteAvecContactUrgence(employeDto.getLienDeParenteAvecContactUrgence())
                    .telephoneUrgent(employeDto.getTelephoneUrgent())
                    .agence(employeDto.getAgence())
                    .codeSite(employeDto.getCodeSite())
                    .villeSite(employeDto.getVilleSite())
                    .chefEquipe(employeDto.getChefEquipe())
                    .managerOps(employeDto.getManagerOps())
                    .poste(employeDto.getPoste())
                    .typeContrat(employeDto.getTypeContrat())
                    .dateEmbauche(employeDto.getDateEmbauche())
                    .dateFinContrat(employeDto.getDateFinContrat())
                    .tempsDeTravail(employeDto.getTempsDeTravail())
                    .horaire(employeDto.getHoraire())
                    .salaireDeBase(employeDto.getSalaireDeBase())
                    .primeTransport(employeDto.getPrimeTransport())
                    .primeAssiduite(employeDto.getPrimeAssiduite())
                    .primeRisque(employeDto.getPrimeRisque())
                    .ribCompteBancaire(employeDto.getRibCompteBancaire())
                    .banque(employeDto.getBanque())
                    .cnssOuIpres(employeDto.getCnssOuIpres())
                    .ipmNumero(employeDto.getIpmNumero())
                    .permisConduire(employeDto.getPermisConduire())
                    .categoriePermis(employeDto.getCategoriePermis())
                    .statut(employeDto.getStatut())
                    .motifSortie(employeDto.getMotifSortie())
                    .dateSortie(employeDto.getDateSortie())
                    .photo(photoData)
                    .observations(employeDto.getObservations())
                    .build();

            return ResponseEntity.ok(employeCompletService.save(employe));

        } catch (IOException e) {
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


    @DeleteMapping("/{agentId}")
    public void deleteEmployee(@PathVariable String agentId) {

        employeCompletService.delete(agentId);
    }

    @PutMapping(value = "/complet/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EmployeComplet> update(
            @PathVariable String id,
            @RequestPart("employe") EmployeComplet employeComplet,
            @RequestPart(value = "photo", required = false) MultipartFile photo
    ) throws IOException {

        EmployeComplet updated = employeCompletService.update(id, employeComplet, photo);
        return ResponseEntity.ok(updated);
    }

}
