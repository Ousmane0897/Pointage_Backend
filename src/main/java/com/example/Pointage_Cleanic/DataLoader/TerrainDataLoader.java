package com.example.Pointage_Cleanic.DataLoader;

import com.example.Pointage_Cleanic.Enum.terrain.CategoriePhyto;
import com.example.Pointage_Cleanic.Enum.terrain.FrequencePassage;
import com.example.Pointage_Cleanic.entities.terrain.ContactClient;
import com.example.Pointage_Cleanic.entities.terrain.CoordonneesGps;
import com.example.Pointage_Cleanic.entities.terrain.CritereEvaluationTerrain;
import com.example.Pointage_Cleanic.entities.terrain.GrilleEvaluationTerrain;
import com.example.Pointage_Cleanic.entities.terrain.ParametresEscalade;
import com.example.Pointage_Cleanic.entities.terrain.ProduitPhytosanitaire;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.terrain.GrilleEvaluationTerrainRepository;
import com.example.Pointage_Cleanic.repositories.terrain.ParametresEscaladeRepository;
import com.example.Pointage_Cleanic.repositories.terrain.ProduitPhytoRepository;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Seed minimal du module Exploitation Terrain : 2 sites, 1 grille générique,
 * 1 produit phytosanitaire homologué, 1 paramétrage d'escalade.
 * Idempotent ({@code count() > 0} → on n'écrase rien).
 */
@Component
@RequiredArgsConstructor
public class TerrainDataLoader implements CommandLineRunner {

    private final SiteClientRepository siteRepository;
    private final GrilleEvaluationTerrainRepository grilleRepository;
    private final ProduitPhytoRepository produitPhytoRepository;
    private final ParametresEscaladeRepository parametresEscaladeRepository;

    @Override
    public void run(String... args) {
        seedSites();
        seedGrilleGenerique();
        seedProduitPhyto();
        seedParametresEscalade();
    }

    private void seedSites() {
        if (siteRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<SiteClient> sites = List.of(
                SiteClient.builder()
                        .code("SITE-DKR-001")
                        .nom("Banque Atlantique — Siège Dakar")
                        .raisonSociale("Banque Atlantique Sénégal")
                        .adresse("Avenue Léopold Sédar Senghor")
                        .ville("Dakar")
                        .pays("Sénégal")
                        .coordonnees(CoordonneesGps.builder().latitude(14.6928).longitude(-17.4467).build())
                        .rayonToleranceM(100)
                        .contactPrincipal(ContactClient.builder()
                                .nom("Awa Ndiaye").fonction("Responsable des services généraux")
                                .telephone("+221770000001").email("awa.ndiaye@example.sn").build())
                        .frequencePassage(FrequencePassage.QUOTIDIEN)
                        .surfaceM2(1200d)
                        .actif(true)
                        .createdAt(now).updatedAt(now)
                        .build(),
                SiteClient.builder()
                        .code("SITE-THS-002")
                        .nom("Centre Commercial — Thiès")
                        .raisonSociale("SCI Les Manguiers")
                        .adresse("Rue du Commerce")
                        .ville("Thiès")
                        .pays("Sénégal")
                        .coordonnees(CoordonneesGps.builder().latitude(14.7910).longitude(-16.9256).build())
                        .rayonToleranceM(150)
                        .contactPrincipal(ContactClient.builder()
                                .nom("Modou Fall").fonction("Gérant")
                                .telephone("+221770000002").build())
                        .frequencePassage(FrequencePassage.HEBDOMADAIRE)
                        .surfaceM2(800d)
                        .actif(true)
                        .createdAt(now).updatedAt(now)
                        .build()
        );
        siteRepository.saveAll(sites);
        System.out.println("Terrain : 2 sites clients créés");
    }

    private void seedGrilleGenerique() {
        if (grilleRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        GrilleEvaluationTerrain grille = GrilleEvaluationTerrain.builder()
                .nom("Grille générique — Propreté")
                .siteId(null) // grille générique
                .criteres(List.of(
                        critere("Propreté des sols", "proprete_sols", 3d),
                        critere("Sanitaires", "sanitaires", 2d),
                        critere("Vitres et surfaces", "vitres", 1d),
                        critere("Gestion des déchets", "dechets", 2d)
                ))
                .noteSeuilConformite(3.5d)
                .actif(true)
                .createdAt(now).updatedAt(now)
                .build();
        grilleRepository.save(grille);
        System.out.println("Terrain : grille d'évaluation générique créée");
    }

    private CritereEvaluationTerrain critere(String libelle, String parametre, double poids) {
        return CritereEvaluationTerrain.builder()
                .libelle(libelle).parametre(parametre).poids(poids)
                .noteMin(0d).noteMax(5d).obligatoire(true)
                .build();
    }

    private void seedProduitPhyto() {
        if (produitPhytoRepository.count() > 0) {
            return;
        }
        produitPhytoRepository.save(ProduitPhytosanitaire.builder()
                .nomCommercial("Roundup Pro")
                .matiereActive("Glyphosate 360 g/L")
                .categorie(CategoriePhyto.HERBICIDE)
                .numeroHomologation("MIRAH-2021-0457")
                .doseRecommandee("3 L/ha")
                .delaiReentreeHeures(6)
                .delaiAvantNouvelleApplicationJours(30)
                .fournisseur("Senchim")
                .actif(true)
                .createdAt(LocalDateTime.now())
                .build());
        System.out.println("Terrain : 1 produit phytosanitaire homologué créé");
    }

    private void seedParametresEscalade() {
        if (parametresEscaladeRepository.count() > 0) {
            return;
        }
        parametresEscaladeRepository.save(ParametresEscalade.builder()
                .delaiRetardMinutes(15)
                .delaiAbsenceMinutes(60)
                .delaiEscaladeNiveau1Minutes(30)
                .delaiEscaladeNiveau2Minutes(120)
                .updatedAt(LocalDateTime.now())
                .build());
        System.out.println("Terrain : paramètres d'escalade par défaut créés");
    }
}