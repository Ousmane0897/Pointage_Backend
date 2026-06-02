package com.example.Pointage_Cleanic.controllers.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.AlerteDelaiPhyto;
import com.example.Pointage_Cleanic.Enum.terrain.CategoriePhyto;
import com.example.Pointage_Cleanic.Enum.terrain.StatutApplicationPhyto;
import com.example.Pointage_Cleanic.entities.terrain.ApplicationPhyto;
import com.example.Pointage_Cleanic.entities.terrain.ProduitPhytosanitaire;
import com.example.Pointage_Cleanic.services.terrain.PhytosanitaireService;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/terrain/phytosanitaire")
@RequiredArgsConstructor
public class PhytosanitaireController {

    private final PhytosanitaireService service;

    // ───────────────────────── Produits ─────────────────────────

    @GetMapping("/produits")
    public List<ProduitPhytosanitaire> listProduits() {
        return service.listProduits();
    }

    @GetMapping("/produits/{id}")
    public ProduitPhytosanitaire getProduit(@PathVariable String id) {
        return service.getProduit(id);
    }

    @PostMapping("/produits")
    public ResponseEntity<ProduitPhytosanitaire> createProduit(@RequestBody ProduitPhytosanitaire produit) {
        return ResponseEntity.status(201).body(service.createProduit(produit));
    }

    @PutMapping("/produits/{id}")
    public ProduitPhytosanitaire updateProduit(@PathVariable String id,
                                               @RequestBody ProduitPhytosanitaire produit) {
        return service.updateProduit(id, produit);
    }

    @DeleteMapping("/produits/{id}")
    public ResponseEntity<Void> deleteProduit(@PathVariable String id) {
        service.deleteProduit(id);
        return ResponseEntity.noContent().build();
    }

    // ───────────────────────── Applications ─────────────────────────

    @GetMapping("/applications")
    public PageResponse<ApplicationPhyto> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String employeId,
            @RequestParam(required = false) String produitId,
            @RequestParam(required = false) CategoriePhyto categorie,
            @RequestParam(required = false) StatutApplicationPhyto statut) {
        return service.listApplications(page, size, dateDebut, dateFin, siteId, employeId, produitId, categorie, statut);
    }

    @GetMapping("/applications/periode")
    public List<ApplicationPhyto> applicationsPeriode(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return service.applicationsPeriode(dateDebut, dateFin);
    }

    @GetMapping("/applications/{id}")
    public ApplicationPhyto getApplication(@PathVariable String id) {
        return service.getApplication(id);
    }

    @PostMapping("/applications")
    public ResponseEntity<ApplicationPhyto> createApplication(@RequestBody ApplicationPhyto application) {
        return ResponseEntity.status(201).body(service.createApplication(application));
    }

    @PutMapping("/applications/{id}")
    public ApplicationPhyto updateApplication(@PathVariable String id, @RequestBody ApplicationPhyto application) {
        return service.updateApplication(id, application);
    }

    @DeleteMapping("/applications/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable String id) {
        service.deleteApplication(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/alertes-delais")
    public List<AlerteDelaiPhyto> alertesDelais() {
        return service.alertesDelais();
    }

    @GetMapping("/registre/pdf")
    public ResponseEntity<byte[]> registrePdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        byte[] pdf = service.registrePdf(dateDebut, dateFin);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"registre-phyto-" + dateDebut + "_" + dateFin + ".pdf\"")
                .body(pdf);
    }
}