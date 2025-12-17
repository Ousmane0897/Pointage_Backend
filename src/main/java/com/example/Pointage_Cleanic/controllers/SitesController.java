package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.entities.Site;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.SiteRepository;
import com.example.Pointage_Cleanic.services.SiteServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/site")
@RequiredArgsConstructor
public class SitesController {

    private final SiteServices siteServices;
    private final SiteRepository siteRepository;

    @PostMapping
    public ResponseEntity<Site> create(@RequestBody Site site) {
        Site site1 = siteServices.save(site);
        return ResponseEntity.status(HttpStatus.CREATED).body(site1);
    }

    @GetMapping
    public ResponseEntity<List<Site>> getAll() {
        List<Site> sites = siteServices.getAll();
        return ResponseEntity.status(HttpStatus.FOUND).body(sites);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Site> getById(@PathVariable String id) {
        Site site = siteServices.getById(id);
        return ResponseEntity.status(HttpStatus.FOUND).body(site);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteSite(@PathVariable String id) {
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Site not found with id:" + id));

        siteRepository.delete(site);
        Map<String, Boolean> response = new HashMap<>();
        response.put("Deleted", Boolean.TRUE);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Site> updateSite(@PathVariable String id, @RequestBody Site siteDetails) {
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id:" + id));

        site.setNom(siteDetails.getNom());
        site.setType(siteDetails.getType());
        site.setEmplacement(siteDetails.getEmplacement());
        site.setNombreEmployes(siteDetails.getNombreEmployes());
        site.setCheffeSite(siteDetails.getCheffeSite());
        site.setFrequenceDuNettoyage(siteDetails.getFrequenceDuNettoyage());

        Site site1 = siteServices.save(site);
        return ResponseEntity.status(HttpStatus.CREATED).body(site1);
    }
}
