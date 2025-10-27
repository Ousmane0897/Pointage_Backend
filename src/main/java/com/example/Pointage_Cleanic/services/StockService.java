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

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        List<MouvementEntreeStock> mouvements = mouvementEntreeStockRepository.findByCodeProduitOrderByDateMouvementAsc(codeProduit);
        int stock = 0;
        for (MouvementEntreeStock m : mouvements) {
            switch (m.getType()) {
                case ENTREE -> stock += m.getQuantite();
                case AJUSTEMENT -> stock = m.getQuantite();
            }
        }
        return Math.max(stock, 0); // Cela garantit que le stock ne devient jamais négatif.Si total est positif Math.max() retourne la valeur de total ou retourne 0 dans le cas contraire
    }

    public List<MouvementEntreeStock> getAllEntree() {

        TypeMouvement var = TypeMouvement.ENTREE;
        return mouvementEntreeStockRepository.findByType(var);
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
                    //.dateDeSortie(dateDeSortie)
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

