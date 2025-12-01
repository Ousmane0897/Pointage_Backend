package com.example.Pointage_Cleanic.services;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import com.example.Pointage_Cleanic.Enum.StatutCommande;
import com.example.Pointage_Cleanic.entities.besoins.BesoinProduit;
import com.example.Pointage_Cleanic.entities.besoins.CollecteBesoins;
import com.example.Pointage_Cleanic.repositories.CollecteBesoinRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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

        // Cloner la liste d'origine pour éviter les modifications ultérieures
        List<BesoinProduit> copie = demande.getProduitsDemandes()
                .stream()
                .map(p -> new BesoinProduit(p.getCodeProduit(), p.getNomProduit(), p.getQuantite()))
                .collect(Collectors.toList());

        demande.setAnciensProduitsDemandes(copie);


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

        CollecteBesoins ancienne = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande introuvable"));

        if (ancienne.getStatut() == StatutCommande.LIVREE) {
            throw new RuntimeException("Impossible de modifier une demande déjà livrée.");
        }

        ancienne.setDestination(nouvelleVersion.getDestination());
        ancienne.setResponsable(nouvelleVersion.getResponsable());
        ancienne.setProduitsDemandes(nouvelleVersion.getProduitsDemandes());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dateFormatee = LocalDate.now().format(formatter);

        String decodedModifiedBy = modifiedBy == null
                ? "SUPERVISEUR"
                : URLDecoder.decode(modifiedBy, StandardCharsets.UTF_8);

        String log = String.format("%s - vérifiée et validée par %s", dateFormatee, decodedModifiedBy);


        ancienne.getHistoriqueModifications().add(log);

        //  Incrémenter le compteur stocké en base
        ancienne.setNombreModifications(ancienne.getNombreModifications() + 1);

        //  Mettre à jour le statut après 3 modifications
        if (ancienne.getNombreModifications() == 3) {
            ancienne.setStatut(StatutCommande.EN_COURS);
        }

        return repository.save(ancienne);
    }


    // Changer statut simple (EN_ATTENTE,EN_COURS,LIVREE)
    public CollecteBesoins updateStatut(String id, StatutCommande statut, String by) {
        CollecteBesoins d = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Demande introuvable"));

        String dateFormatee = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String heure = ZonedDateTime.now(ZoneId.of("Africa/Dakar"))
                .format(DateTimeFormatter.ofPattern("HH:mm"));

        // Bloquer si déjà livrée
        if (d.getStatut() == StatutCommande.LIVREE) {
            throw new RuntimeException("Impossible de changer le statut d'une demande livrée.");
        }

        // Enregistrer date/heure seulement si nouveau statut = LIVREE
        if (statut == StatutCommande.LIVREE) {
            d.setDateLivraison(dateFormatee);
            d.setHeureLivraison(heure);
        }

        d.setStatut(statut);
        d.getHistoriqueModifications()
                .add(String.format("%s %s - Statut → %s par %s", dateFormatee, heure, statut, by));

        return repository.save(d);
    }

    // Recupérer l'historique modification

    public List<String> getHistorique(String id) {

        CollecteBesoins d = repository.findById(id).orElseThrow(() -> new RuntimeException("Demande introuvable"));

        return d.getHistoriqueModifications();

    }

    public List<CollecteBesoins> getHistoriques() {
        return repository.findByStatut(StatutCommande.LIVREE);
    }



}
