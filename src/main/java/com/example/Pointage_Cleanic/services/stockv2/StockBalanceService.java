package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.exception.StockOperationException;
import com.example.Pointage_Cleanic.repositories.stockv2.StockParSiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gestion bas-niveau des soldes de stock par couple (produit, site).
 * Source de vérité unique partagée par le catalogue (stock initial),
 * les mouvements et la clôture d'inventaire.
 * <p>
 * Un {@code siteId} null représente le bucket « non affecté » (ex: stock initial d'import
 * sans site). Il compte dans le solde consolidé du produit.
 */
@Service
@RequiredArgsConstructor
public class StockBalanceService {

    private final StockParSiteRepository repository;

    public Optional<StockParSite> find(String produitId, String siteId) {
        return repository.findByProduitIdAndSiteId(produitId, siteId);
    }

    public double quantite(String produitId, String siteId) {
        return repository.findByProduitIdAndSiteId(produitId, siteId)
                .map(StockParSite::getQuantite)
                .orElse(0.0);
    }

    public double quantiteTotale(String produitId) {
        return repository.findByProduitId(produitId).stream()
                .mapToDouble(StockParSite::getQuantite)
                .sum();
    }

    public List<StockParSite> soldesDuProduit(String produitId) {
        return repository.findByProduitId(produitId);
    }

    /** Vérifie qu'au moins {@code qte} est disponible sur le couple, sinon 422. */
    public void verifierDisponibilite(String produitId, String siteId, double qte, String libelleSite) {
        double dispo = quantite(produitId, siteId);
        if (dispo < qte) {
            throw new StockOperationException(
                    "Stock insuffisant" + (libelleSite == null ? "" : " sur " + libelleSite)
                            + " : disponible " + dispo + ", demandé " + qte);
        }
    }

    /**
     * Applique un delta (positif = entrée, négatif = sortie) au solde du couple (produit, site),
     * en upsert. Retourne l'entité persistée. Ne contrôle PAS la disponibilité (à la charge de l'appelant).
     */
    public StockParSite appliquerDelta(String produitId, String siteId, double delta) {
        StockParSite solde = repository.findByProduitIdAndSiteId(produitId, siteId)
                .orElseGet(() -> StockParSite.builder()
                        .produitId(produitId)
                        .siteId(siteId)
                        .quantite(0.0)
                        .build());
        solde.setQuantite(solde.getQuantite() + delta);
        solde.setDateMaj(LocalDateTime.now());
        return repository.save(solde);
    }
}
