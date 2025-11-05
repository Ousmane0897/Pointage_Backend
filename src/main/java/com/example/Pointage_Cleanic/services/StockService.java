package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.Dto.MouvementStock;
import com.example.Pointage_Cleanic.Enum.MotifMouvementSortieStock;
import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stock.MouvementEntreeStock;
import com.example.Pointage_Cleanic.entities.stock.MouvementSortieStock;
import com.example.Pointage_Cleanic.entities.stock.Produit;
import com.example.Pointage_Cleanic.repositories.MouvementEntreeStockRepository;
import com.example.Pointage_Cleanic.repositories.MouvementSortieStockRepository;
import com.example.Pointage_Cleanic.repositories.ProduitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockService {

    private final ProduitRepository produitRepository;
    private final MouvementEntreeStockRepository mouvementEntreeStockRepository;
    private final MouvementSortieStockRepository mouvementSortieStockRepository;
    private final MongoOperations mongoOperations; // Utile si on veut interagir avec MongoDB dans ton code

    /**
     * Enregistrer un mouvement et mettre à jour snapshot quantite (atomique via $inc / upsert)
     */
    @Transactional
    // ✅ Enregistrer un mouvement de stock (Entrée, Sortie ou Ajustement)
    public MouvementEntreeStock enregistrerMouvement(MouvementEntreeStock mouvement) {

        // Vérification du produit concerné
        Produit produit = produitRepository.findByCodeProduit(mouvement.getCodeProduit())
                .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé"));

        // Récupère la quantité actuelle du produit
        int ancienneQuantite = produit.getQuantiteSnapshot() != null ? produit.getQuantiteSnapshot() : 0;
        int nouvelleQuantite = ancienneQuantite;

        // ----------------------------------------------------
        // 🔹 CAS 1 : ENTRÉE EN STOCK
        // ----------------------------------------------------
        if (mouvement.getType() == TypeMouvement.ENTREE) {
            nouvelleQuantite = ancienneQuantite + mouvement.getQuantite();
        }

        // ----------------------------------------------------
        // 🔹 CAS 2 : AJUSTEMENT
        // ----------------------------------------------------

        else if (mouvement.getType() == TypeMouvement.AJUSTEMENT) {
            // Pour un ajustement, le champ "motif" ou "source" doit expliquer pourquoi
            // Exemple : "Correction d’inventaire", "Erreur de saisie", etc.
            // L’ajustement peut être positif (ajout) ou négatif (retrait)
            nouvelleQuantite = ancienneQuantite + mouvement.getQuantite();

            // Si la quantité devient négative → correction automatique à zéro
            if (nouvelleQuantite < 0) {
                nouvelleQuantite = 0;
            }
        }
        // ----------------------------------------------------
        // 🔹 CAS 3 : SORTIE DE STOCK
        // ----------------------------------------------------

        /* else if (mouvement.getType() == TypeMouvement.SORTIE) {
            // Vérifie que le stock est suffisant
            if (ancienneQuantite < mouvement.getQuantite()) {
                throw new IllegalArgumentException(
                        "Stock insuffisant pour effectuer la sortie du produit : " + produit.getNomProduit());
            }

            nouvelleQuantite = ancienneQuantite - mouvement.getQuantite();
        }*/


        // ----------------------------------------------------
        // 🔸 Mise à jour du produit
        // ----------------------------------------------------
        produit.setQuantiteSnapshot(nouvelleQuantite);
        produitRepository.save(produit);

        // ----------------------------------------------------
        // 🔸 Sauvegarde du mouvement dans l’historique
        // ----------------------------------------------------
        mouvement.setDateMouvement(mouvement.getDateMouvement());
        return mouvementEntreeStockRepository.save(mouvement);
    }


    /**
     * Calcule le stock théorique d'un produit à partir des mouvements (fallback si pas de snapshot)
     */
    public int calculerStockTheoriqueFromMovements(String codeProduit) {
        // Récupère toutes les entrées pour ce produit
        List<MouvementEntreeStock> entrees = mouvementEntreeStockRepository
                .findByCodeProduitOrderByDateMouvementAsc(codeProduit);

        // Récupère toutes les sorties pour ce produit
        List<MouvementSortieStock> sorties = mouvementSortieStockRepository
                .findByCodeProduitOrderByDateMouvementAsc(codeProduit);

        int stock = 0;

        // 🔹 On commence par additionner les entrées
        for (MouvementEntreeStock entree : entrees) {
            switch (entree.getType()) {
                case ENTREE -> stock += entree.getQuantite();      // Entrée = ajout
                case AJUSTEMENT -> stock = entree.getQuantite();   // Ajustement = stock fixé manuellement
            }
        }

        // 🔹 Ensuite, on soustrait les sorties
        for (MouvementSortieStock sortie : sorties) {
            switch (sortie.getTypeMouvement()) {
                case SORTIE -> stock -= sortie.getQuantite();       // Sortie = retrait
                //case AJUSTEMENT_NEGATIF -> stock = sortie.getQuantite(); // si tu veux gérer un ajustement via sortie
            }
        }

        // 🔹 Empêche le stock négatif
        return Math.max(stock, 0);
    }


    public List<MouvementEntreeStock> getAllEntree() {

        TypeMouvement var = TypeMouvement.ENTREE;
        return mouvementEntreeStockRepository.findByType(var);
    }

    public List<MouvementSortieStock> getAllSorties() {

        TypeMouvement var = TypeMouvement.SORTIE;
        return mouvementSortieStockRepository.findByTypeMouvement(var);
    }

    /**
     * Retourne le stock courant en préférant le snapshot (performant), sinon calcule
     */
    public int getStockCurrent(String codeProduit) {
        Produit p = produitRepository.findByCodeProduit(codeProduit).orElseThrow(() -> new IllegalArgumentException("Produit non trouvé"));
        if (p.getQuantiteSnapshot() != null) return p.getQuantiteSnapshot();
        return calculerStockTheoriqueFromMovements(codeProduit);
    }

    // 🔎 Récupérer le stock actuel
   /* public int getStockCurrent(String produitId) {
        return produitRepository.findById(produitId)
                .map(Produit::getQuantiteSnapshot)
                .orElse(0);
    }*/

    // ==========================================================
    // 📈 1️⃣ Évolution du stock (par produit)
    // ==========================================================
    public Map<String, Object> getStockEvolutionReport(String codeProduit) {
        List<MouvementEntreeStock> entrees = mouvementEntreeStockRepository.findByCodeProduit(codeProduit);
        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findByCodeProduit(codeProduit);

        // Utiliser TreeMap pour garder l'ordre chronologique
        Map<String, Integer> evolution = new TreeMap<>((a, b) -> {
            try {
                // Comparaison basée sur l'ordre réel des mois (format "MMMM yyyy")
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
                YearMonth ym1 = YearMonth.parse(a, formatter);
                YearMonth ym2 = YearMonth.parse(b, formatter);
                return ym1.compareTo(ym2);
            } catch (Exception e) {
                return a.compareTo(b);
            }
        });

        // Ajouter les entrées (positives)
        entrees.forEach(e -> {
            String mois = getMoisAnneeFrancais(e.getDateMouvement());
            evolution.put(mois, evolution.getOrDefault(mois, 0) + e.getQuantite());
        });

        // Ajouter les sorties (négatives)
        sorties.forEach(s -> {
            String mois = getMoisAnneeFrancais(s.getDateMouvement());
            evolution.put(mois, evolution.getOrDefault(mois, 0) - s.getQuantite());
        });

        return Map.of(
                "codeProduit", codeProduit,
                "labels", evolution.keySet(),
                "data", evolution.values()
        );
    }

    // ==========================================================
// 🧩 Méthode utilitaire pour formater les dates en FRANÇAIS
// ==========================================================
    private String getMoisAnneeFrancais(Instant instant) {
        LocalDate date = instant.atZone(ZoneId.systemDefault()).toLocalDate();

        // Format du mois en lettres françaises (ex: "janvier 2025")
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
        String moisAnnee = date.format(formatter);

        // Mettre la première lettre en majuscule (ex: "Janvier 2025")
        return moisAnnee.substring(0, 1).toUpperCase() + moisAnnee.substring(1);
    }


    /**
     * 🏢 Répartition des sorties par destination (agence, chantier…)
     */
    public Map<String, Object> getSortiesParDestination(Integer mois, Integer annee) {
        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findAll().stream()
                .filter(s -> {
                    LocalDateTime date = LocalDateTime.ofInstant(s.getDateMouvement(), ZoneId.systemDefault());
                    return date.getMonthValue() == mois && date.getYear() == annee;
                })
                .toList();

        Map<String, Integer> repartition = sorties.stream()
                .collect(Collectors.groupingBy(
                        s -> Optional.ofNullable(s.getDestination()).orElse("Inconnue"),
                        Collectors.summingInt(MouvementSortieStock::getQuantite)
                ));

        return Map.of(
                "labels", repartition.keySet(),
                "data", repartition.values()
        );
    }



    // ==========================================================
    // 🏆 3️⃣ Top 5 produits les plus sortis sur une période donnée
    // ==========================================================
    public Map<String, Object> getTopProduitsSortisParPeriode(int mois, int annee) {
        LocalDate debut = LocalDate.of(annee, mois, 1);
        LocalDate fin = debut.withDayOfMonth(debut.lengthOfMonth());

        Instant start = debut.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = fin.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant();

        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findByTypeMouvementAndDateMouvementBetween(
                TypeMouvement.SORTIE,
                start,
                end
        );

        if (sorties.isEmpty()) {
            return Map.of("labels", List.of(), "data", List.of());
        }

        Map<String, Integer> totalSorties = sorties.stream()
                .collect(Collectors.groupingBy(
                        MouvementSortieStock::getNomProduit,
                        Collectors.summingInt(MouvementSortieStock::getQuantite)
                ));

        List<Map.Entry<String, Integer>> top5 = totalSorties.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .toList();

        List<String> labels = top5.stream().map(Map.Entry::getKey).toList();
        List<Integer> data = top5.stream().map(Map.Entry::getValue).toList();

        return Map.of("labels", labels, "data", data);
    }

    /**
     * Option A – Vue instantanée (snapshot) : quantité par produit pour un mois donné → 📊 Bar chart (labels = produits)
     */
    public Map<String, Object> getSnapshotByMonth( int mois, int annee) {
        List<MouvementEntreeStock> entrees = mouvementEntreeStockRepository.findAll();
        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findAll();

        Map<String, Integer> bilan = new HashMap<>();

        // 🔹 Ajouter les entrées
        entrees.stream()
                .filter(e -> isSameMonth(e.getDateMouvement(), mois, annee))
                .forEach(e -> bilan.put(e.getNomProduit(),
                        bilan.getOrDefault(e.getNomProduit(), 0) + e.getQuantite()));

        // 🔸 Ajouter les sorties
        sorties.stream()
                .filter(s -> isSameMonth(s.getDateMouvement(), mois, annee))
                .forEach(s -> bilan.put(s.getNomProduit(),
                        bilan.getOrDefault(s.getNomProduit(), 0) - s.getQuantite()));

        return Map.of(
                "periode", getMoisAnneeFrancais(LocalDate.of(annee, mois, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()),
                "labels", bilan.keySet(),
                "data", bilan.values()
        );
    }

    private boolean isSameMonth(Instant date, int mois, int annee) {
        LocalDate d = date.atZone(ZoneId.systemDefault()).toLocalDate();
        return d.getMonthValue() == mois && d.getYear() == annee;
    }


    /**
     * Évolution temporelle : suivi de l’évolution de chaque produit dans le temps → 📈 Line chart multi-produits
     */


    private String getMoisNom(int mois) {
        String[] moisFr = {
                "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
        };
        return moisFr[mois - 1];
    }

    /**
     * Option B – Évolution temporelle : suivi de l’évolution de chaque produit dans le temps → 📈 Line chart multi-produits
     */
    public Map<String, Object> getEvolutionParProduits() {
        List<MouvementEntreeStock> entrees = mouvementEntreeStockRepository.findAll();
        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findAll();

        // Ensemble unique des produits
        Set<String> produits = new HashSet<>();
        entrees.forEach(e -> produits.add(e.getNomProduit()));
        sorties.forEach(s -> produits.add(s.getNomProduit()));

        // Calcul du stock global (entrées - sorties)
        Map<String, Integer> stockParProduit = new HashMap<>();

        for (MouvementEntreeStock entree : entrees) {
            stockParProduit.merge(entree.getNomProduit(), entree.getQuantite(), Integer::sum);
        }

        for (MouvementSortieStock sortie : sorties) {
            stockParProduit.merge(sortie.getNomProduit(), -sortie.getQuantite(), Integer::sum);
        }

        // Trier par quantité CROISSANTE
        List<Map.Entry<String, Integer>> sortedList = stockParProduit.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue()) // pas de .reversed()
                .toList();

        // Extraire les produits (labels) et quantités (data)
        List<String> labels = sortedList.stream()
                .map(Map.Entry::getKey)
                .toList();

        List<Integer> data = sortedList.stream()
                .map(Map.Entry::getValue)
                .toList();

        // Créer le dataset
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("label", "Stock global");
        dataset.put("data", data);

        // Retour final compatible avec Chart.js
        return Map.of(
                "labels", labels,
                "datasets", List.of(dataset)
        );
    }


    /**
     * Rapport mensuel
     */
    public Map<String, Object> getRapportMensuel(int mois, int annee) {
        List<MouvementEntreeStock> entrees = mouvementEntreeStockRepository.findAll();
        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findAll();

        int totalEntrees = entrees.stream()
                .filter(e -> isSameMonth(e.getDateMouvement(), mois, annee))
                .mapToInt(MouvementEntreeStock::getQuantite)
                .sum();

        int totalSorties = sorties.stream()
                .filter(s -> isSameMonth(s.getDateMouvement(), mois, annee))
                .mapToInt(MouvementSortieStock::getQuantite)
                .sum();

        int solde = totalEntrees - totalSorties;

        // 🔹 Top 5 produits sortis du mois
        Map<String, Integer> topProduits = sorties.stream()
                .filter(s -> isSameMonth(s.getDateMouvement(), mois, annee))
                .collect(Collectors.groupingBy(
                        MouvementSortieStock::getNomProduit,
                        Collectors.summingInt(MouvementSortieStock::getQuantite)
                ));

        List<Map.Entry<String, Integer>> top5 = topProduits.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .toList();

        return Map.of(
                "mois", mois,
                "annee", annee,
                "totalEntrees", totalEntrees,
                "totalSorties", totalSorties,
                "solde", solde,
                "topProduits", top5
        );
    }

   /* private boolean isSameMonth(Instant date, int mois, int annee) {
        LocalDate d = date.atZone(ZoneId.systemDefault()).toLocalDate();
        return d.getMonthValue() == mois && d.getYear() == annee;
    }*/



    /**
     * 🧠 Retourne une vue d’ensemble du stock :
     * - Quantité actuelle
     * - Seuil minimum
     * - État (normal, bas, rupture)
     * - Derniers mouvements
     */
    public List<Map<String, Object>> getSuiviGlobal() {
        List<Produit> produits = produitRepository.findAll();

        return produits.stream().map(produit -> {
            int stock = getStockCurrent(produit.getCodeProduit());
            boolean seuilAtteint = produit.getSeuilMinimum() != null && stock <= produit.getSeuilMinimum();

            /*List<MouvementStock> derniersMvts = mouvementStockRepository
                    .findTop5ByProduitIdOrderByDateMouvementDesc(produit.getId());*/

            Map<String, Object> suivi = new HashMap<>();
            suivi.put("id", produit.getId());
            suivi.put("codeProduit", produit.getCodeProduit());
            suivi.put("nomProduit", produit.getNomProduit());
            suivi.put("categorie", produit.getCategorie());
            suivi.put("stockActuel", stock);
            suivi.put("seuilMinimum", produit.getSeuilMinimum());
            suivi.put("etat", seuilAtteint ? (stock == 0 ? "RUPTURE" : "BAS") : "NORMAL");
            //suivi.put("derniersMouvements", derniersMvts);

            return suivi;
        }).collect(Collectors.toList());
    }



    public List<MouvementEntreeStock> getHistorique(String codeProduit) {
        return mouvementEntreeStockRepository.findByCodeProduitOrderByDateMouvementAsc(codeProduit);
    }

    /**
     * Vérifie si le stock est inférieur ou égale au seuil minimum
     */
    public boolean isUnderReorderPoint(String produitId) {
        Produit p = produitRepository.findById(produitId).orElseThrow(() -> new IllegalArgumentException("Produit non trouvé"));
        int stock = getStockCurrent(produitId);
        return p.getSeuilMinimum() != null && stock <= p.getSeuilMinimum();
    }

    // 🔹 Sortie simple d’un produit
    public MouvementSortieStock sortieSimple(MouvementSortieStock mouvement) {
        Produit produit = produitRepository.findByCodeProduit(mouvement.getCodeProduit())
                .orElseThrow(() -> new IllegalArgumentException("Produit introuvable"));

        int stockActuel = produit.getQuantiteSnapshot() != null ? produit.getQuantiteSnapshot() : 0;

        if (mouvement.getQuantite() > stockActuel) {
            throw new IllegalArgumentException("Stock insuffisant pour le produit : " + produit.getNomProduit());
        }

        LocalDate today = LocalDate.now();
        // Récupère le mois
        String moisFrancais = today.getMonth().getDisplayName(TextStyle.FULL, Locale.FRANCE);

        // Mise à jour du stock
        produit.setQuantiteSnapshot(stockActuel - mouvement.getQuantite());
        produitRepository.save(produit);

        // Enregistrement du mouvement
        mouvement.setTypeMouvement(TypeMouvement.SORTIE);
        mouvement.setMois(moisFrancais);
        mouvement.setDateMouvement(Instant.now());
        return mouvementSortieStockRepository.save(mouvement);
    }

    // ✅ Sortie multiple (batch)
    public List<MouvementSortieStock> sortieBatch(
            List<MouvementStock> produits,
            String destination,
            String responsable,
            //Instant dateDeSortie,
            TypeMouvement typeMouvement,
            MotifMouvementSortieStock motifSortieStock) {

        List<MouvementSortieStock> mouvementsAEnregistrer = new ArrayList<>();

        for (MouvementStock dto : produits) {
            // 🔹 Vérifier que le produit existe
            Produit produit = produitRepository.findByCodeProduit(dto.getCodeProduit())
                    .orElseThrow(() -> new IllegalArgumentException("Produit introuvable : " + dto.getCodeProduit()));

            int stockActuel = produit.getQuantiteSnapshot() != null ? produit.getQuantiteSnapshot() : 0;

            // 🔹 Vérifier que la quantité est disponible
            if (dto.getQuantite() > stockActuel) {
                throw new IllegalArgumentException("Stock insuffisant pour le produit : " + produit.getNomProduit());
            }

            // 🔹 Mettre à jour le stock
            produit.setQuantiteSnapshot(stockActuel - dto.getQuantite());
            produitRepository.save(produit);

            LocalDate today = LocalDate.now();
            // Récupère le mois
            String moisFrancais = today.getMonth().getDisplayName(TextStyle.FULL, Locale.FRANCE);

            // 🔹 Créer l’enregistrement du mouvement complet
            MouvementSortieStock mouvement = MouvementSortieStock.builder()
                    .codeProduit(dto.getCodeProduit())
                    .nomProduit(dto.getNomProduit())
                    .quantite(dto.getQuantite())
                    .typeMouvement(typeMouvement)
                    .motifSortieStock(motifSortieStock)
                    .destination(destination)
                    .responsable(responsable)
                    .mois(moisFrancais)
                    .dateMouvement(Instant.now())
                    .build();

            mouvementsAEnregistrer.add(mouvement); // Chaque produit de la liste des produits est ajouté individuellement dans mouvementsAEnregistrer
        }

        // 🔹 Sauvegarde groupée des mouvements
        return mouvementSortieStockRepository.saveAll(mouvementsAEnregistrer);
    }


    // 🔹 Historique des sorties
    public List<MouvementSortieStock> getSorties() {
        return mouvementSortieStockRepository.findAll()
                .stream()
                .filter(m -> m.getTypeMouvement() == TypeMouvement.SORTIE)
                .toList();
    }

}

