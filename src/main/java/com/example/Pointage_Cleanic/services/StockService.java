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
     * 1️⃣ Quantité d’un produit donné par mois pour chaque destination (année complète)
     */
    public Map<String, Object> getQuantiteProduitParDestinationParMois(String nomProduit, String destination , int annee) {

        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findByNomProduit(nomProduit)
                .stream()
                .filter(s -> {
                    LocalDate d = s.getDateMouvement().atZone(ZoneId.systemDefault()).toLocalDate();
                    return d.getYear() == annee &&
                            Objects.equals(
                                    Optional.ofNullable(s.getDestination()).orElse("Inconnue"),
                                    destination
                            );
                })
                .toList();

        // Mois → Quantité (on initialise avec 0 pour tous les mois)
        Map<String, Integer> parMois = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            parMois.put(getMoisNom(m), 0);
        }

        // Ajout des quantités
        for (MouvementSortieStock s : sorties) {
            LocalDate date = s.getDateMouvement().atZone(ZoneId.systemDefault()).toLocalDate();
            String mois = getMoisNom(date.getMonthValue());
            parMois.put(mois, parMois.get(mois) + s.getQuantite());
        }

        return Map.of(
                "labels", parMois.keySet(),    // ex: Janvier, Février, ...
                "data", parMois.values()      // ex: 12, 0, 5, ...
        );
    }


    /**
     * 2️⃣ Consommation totale d’une destination pour tous les produits par mois (année)
     */
    public Map<String, Object> getConsommationParDestinationParMois(String destination, int annee) {

        // Liste fixe des mois
        List<String> moisLabels = Arrays.asList(
                "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"
        );

        // Initialiser les quantités à 0
        Map<String, Integer> totalParMois = new LinkedHashMap<>();
        moisLabels.forEach(m -> totalParMois.put(m, 0));

        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findAll();

        for (MouvementSortieStock s : sorties) {
            LocalDate d = s.getDateMouvement().atZone(ZoneId.systemDefault()).toLocalDate();
            if (d.getYear() != annee) continue;
            if (!Objects.equals(s.getDestination(), destination)) continue;

            String mois = getMoisNom(d.getMonthValue());
            totalParMois.put(mois, totalParMois.get(mois) + s.getQuantite());
        }

        return Map.of(
                "labels", moisLabels,
                "data", new ArrayList<>(totalParMois.values())
        );
    }


    /**
     * 🏢 Répartition des sorties par destination en pie (agence, chantier…)
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



    private boolean isSameMonth(Instant date, int mois, int annee) {
        LocalDate d = date.atZone(ZoneId.systemDefault()).toLocalDate();
        return d.getMonthValue() == mois && d.getYear() == annee;
    }


    /**
     * 🏢 Répartition des sorties par destination en Bar (agence, chantier…)
     */
    public Map<String, Object> getSortiesParDestinationBar(Integer mois, Integer annee) {
        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findAll().stream()
                .filter(s -> {
                    LocalDateTime date = LocalDateTime.ofInstant(s.getDateMouvement(), ZoneId.systemDefault());
                    return date.getMonthValue() == mois && date.getYear() == annee;
                })
                .toList();

        if (sorties.isEmpty()) {
            return Map.of("labels", List.of(), "datasets", List.of());
        }

        Map<String, Integer> repartition = sorties.stream()
                .collect(Collectors.groupingBy(
                        s -> Optional.ofNullable(s.getDestination()).orElse("Inconnue"),
                        Collectors.summingInt(MouvementSortieStock::getQuantite)
                ));

        List<Map.Entry<String, Integer>> sorted = repartition.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        List<String> labels = sorted.stream().map(Map.Entry::getKey).toList();
        List<Integer> data = sorted.stream().map(Map.Entry::getValue).toList();

        Map<String, Object> dataset = Map.of(
                "label", "Quantité totale sortie",
                "data", data
        );

        return Map.of(
                "labels", labels,
                "datasets", List.of(dataset)
        );
    }


    /**
     * 🧱 Classement des quantités par destinations pour un produit donné et un mois spécifique.
     * Bar chart : X = Destinations, Y = Quantité sortie.
     */
    public Map<String, Object> getClassementDestinationsParProduitEtMois(String nomProduit, Integer mois, Integer annee) {

        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findAll().stream()
                .filter(s -> {
                    LocalDateTime date = LocalDateTime.ofInstant(s.getDateMouvement(), ZoneId.systemDefault());
                    return date.getMonthValue() == mois
                            && date.getYear() == annee
                            && s.getNomProduit().equalsIgnoreCase(nomProduit);
                })
                .toList();

        if (sorties.isEmpty()) {
            return Map.of("labels", List.of(), "datasets", List.of());
        }

        // Regrouper par destination
        Map<String, Integer> totalParDestination = sorties.stream()
                .collect(Collectors.groupingBy(
                        s -> Optional.ofNullable(s.getDestination()).orElse("Inconnue"),
                        Collectors.summingInt(MouvementSortieStock::getQuantite)
                ));

        // Trier par quantité décroissante
        List<Map.Entry<String, Integer>> sorted = totalParDestination.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        List<String> labels = sorted.stream().map(Map.Entry::getKey).toList();
        List<Integer> data = sorted.stream().map(Map.Entry::getValue).toList();

        Map<String, Object> dataset = Map.of(
                "label", "Quantité sortie de " + nomProduit,
                "data", data
        );

        return Map.of(
                "labels", labels,
                "datasets", List.of(dataset)
        );
    }


    /**
     * 📊 Classement des quantités par destinations pour un produit donné et
     * une période spécifique (par ex: de janvier à Mai). (bar chart).
     */
    public Map<String, Object> getConsommationProduitParPeriode(String nomProduit, int moisDebut, int moisFin, int annee) {
        List<MouvementSortieStock> sorties = mouvementSortieStockRepository.findAll().stream()
                .filter(s -> {
                    LocalDateTime date = LocalDateTime.ofInstant(s.getDateMouvement(), ZoneId.systemDefault());
                    int mois = date.getMonthValue();
                    return date.getYear() == annee
                            && mois >= moisDebut
                            && mois <= moisFin
                            && s.getNomProduit().equalsIgnoreCase(nomProduit);
                })
                .toList();

        if (sorties.isEmpty()) {
            return Map.of("labels", List.of(), "datasets", List.of());
        }

        // Regrouper par destination
        Map<String, Integer> consommationParDestination = sorties.stream()
                .collect(Collectors.groupingBy(
                        s -> Optional.ofNullable(s.getDestination()).orElse("Inconnue"),
                        Collectors.summingInt(MouvementSortieStock::getQuantite)
                ));

        // Trier par quantité décroissante
        List<Map.Entry<String, Integer>> sorted = consommationParDestination.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        List<String> labels = sorted.stream().map(Map.Entry::getKey).toList();
        List<Integer> data = sorted.stream().map(Map.Entry::getValue).toList();

        Map<String, Object> dataset = Map.of(
                "label", "Quantité totale de " + nomProduit + " (" + getMoisNom(moisDebut) + " - " + getMoisNom(moisFin) + ")",
                "data", data
        );

        return Map.of(
                "labels", labels,
                "datasets", List.of(dataset)
        );
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
            String beneficaire,
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
                    .beneficaire(beneficaire)
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

