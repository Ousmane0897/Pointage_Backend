package com.example.Pointage_Cleanic.controllers;

import com.example.Pointage_Cleanic.Enum.StatutCommande;
import com.example.Pointage_Cleanic.entities.besoins.CollecteBesoins;
import com.example.Pointage_Cleanic.services.CollecteBesoinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/besoins")
@RequiredArgsConstructor
public class CollecteBesoinController {

    private final CollecteBesoinService service;

    @PostMapping
    public CollecteBesoins creer(@RequestBody CollecteBesoins demande, @RequestParam String createdby) {
        return service.creerDemande(demande,createdby);
    }

    @GetMapping
    public List<CollecteBesoins> lister() {
        return service.getAll();
    }

    @GetMapping("/moisActuel")
    public List<CollecteBesoins> DemandesDuMoisActuel() {
        return service.getDemandesDuMois();
    }

    @GetMapping("/{id}")
    public CollecteBesoins get(@PathVariable String id) { return service.getById(id); }

    @GetMapping("/historique-modification/{id}")
    public List<String> getHistorique(@PathVariable String id) { return service.getHistorique(id); }

    @GetMapping("/destination/{nom}")
    public List<CollecteBesoins> parDestination(@PathVariable String nom) {
        return service.getByDestination(nom);
    }

    @PatchMapping("/statut/{id}")
    public CollecteBesoins modifierStatut(
            @PathVariable String id,
            @RequestBody Map<String, String> body
    ) {
        StatutCommande statut = StatutCommande.valueOf(body.get("statut"));
        String modifiedBy = body.get("modifiedBy");

        return service.updateStatut(id, statut, modifiedBy);
    }


    @PutMapping("/{id}")
    public CollecteBesoins modifier(@PathVariable String id, @RequestBody CollecteBesoins demande, @RequestParam String modifiedBy) {
        return service.modifierDemande(id, demande, modifiedBy);
    }

}
