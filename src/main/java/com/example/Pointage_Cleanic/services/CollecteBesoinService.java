package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Enum.StatutCommande;
import com.example.Pointage_Cleanic.entities.besoins.CollecteBesoins;
import com.example.Pointage_Cleanic.repositories.CollecteBesoinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CollecteBesoinService {

    private final CollecteBesoinRepository repository;

    public CollecteBesoins creerDemande(CollecteBesoins demande, String createdby) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dateFormatee = LocalDate.now().format(formatter);

        String moisActuel = LocalDate.now().getMonth().getDisplayName(
                TextStyle.FULL, Locale.FRENCH
        );


        demande.setMoisActuel(moisActuel);
        demande.setStatut(StatutCommande.EN_ATTENTE);
        demande.setDateDemande(dateFormatee);

        if (demande.getHistoriqueModifications() == null) {
            demande.setHistoriqueModifications(new ArrayList<>());
        }


        String log = String.format("%s - Créée par %s",
                dateFormatee,
                createdby == null ? "AGENT" : createdby
        );

        demande.getHistoriqueModifications().add(log);
        return repository.save(demande);
    }


    public List<CollecteBesoins> getAll() {
        return repository.findAll();
    }

    public List<CollecteBesoins> getDemandesDuMois() {
        String moisActuel = LocalDate.now().getMonth().getDisplayName(
                TextStyle.FULL, Locale.FRENCH
        );

        return repository.findByMoisActuel(moisActuel);
    }



    public CollecteBesoins getById(String id) { return repository.findById(id).orElseThrow(() -> new RuntimeException("Introuvable")); }


    public List<CollecteBesoins> getByDestination(String destination) {
        return repository.findByDestination(destination);
    }

    // Modifier par supérieur (autorisé si pas LIVREE)
    public CollecteBesoins modifierDemande(String id, CollecteBesoins nouvelleVersion, String modifiedBy) {
        CollecteBesoins ancienne = repository.findById(id).orElseThrow(() -> new RuntimeException("Demande introuvable"));
        if (ancienne.getStatut() == StatutCommande.LIVREE) {
            throw new RuntimeException("Impossible de modifier une demande déjà livrée.");
        }
        ancienne.setDestination(nouvelleVersion.getDestination());
        ancienne.setResponsable(nouvelleVersion.getResponsable());
        ancienne.setProduitsDemandes(nouvelleVersion.getProduitsDemandes());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dateFormatee = LocalDate.now().format(formatter);

        String log = String.format("%s - Modifiée par %s", dateFormatee, modifiedBy == null ? "SUPERVISEUR" : modifiedBy);
        ancienne.getHistoriqueModifications().add(log);
        return repository.save(ancienne);
    }

    // Changer statut simple (EN_COURS, EN_ATTENTE)
    public CollecteBesoins updateStatut(String id, StatutCommande statut, String by) {
        CollecteBesoins d = repository.findById(id).orElseThrow(() -> new RuntimeException("Demande introuvable"));
        if (d.getStatut() == StatutCommande.LIVREE) {
            throw new RuntimeException("Impossible de changer le statut d'une demande livrée.");
        }


        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dateFormatee = LocalDate.now().format(formatter);

        d.setStatut(statut);
        d.getHistoriqueModifications().add(String.format("%s - Statut->%s par %s", dateFormatee, statut, by));
        return repository.save(d);
    }

    // Recupérer l'historique modification

    public List<String> getHistorique(String id) {

        CollecteBesoins d = repository.findById(id).orElseThrow(() -> new RuntimeException("Demande introuvable"));

        return d.getHistoriqueModifications();

    }



}
