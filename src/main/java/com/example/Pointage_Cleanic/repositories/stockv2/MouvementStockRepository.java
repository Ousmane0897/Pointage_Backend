package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MouvementStockRepository extends MongoRepository<MouvementStock, String> {
    List<MouvementStock> findByProduitIdOrderByDateDesc(String produitId);

    List<MouvementStock> findByChantierId(String chantierId);

    /** Mouvements générés par la validation d'un bon (7.4) — base du contre-passement. */
    List<MouvementStock> findByBonId(String bonId);

    /** Mouvements d'ajustement générés par la clôture d'un inventaire (7.3). */
    List<MouvementStock> findByInventaireId(String inventaireId);

    /**
     * Repli pour les inventaires clôturés <b>avant</b> l'ajout de {@code inventaireId} : à l'époque,
     * seul le commentaire « Ajustement inventaire {reference} » rattachait le mouvement.
     */
    List<MouvementStock> findByCommentaire(String commentaire);
}
