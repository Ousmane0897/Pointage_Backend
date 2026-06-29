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

    /** Répartition d'un débit en repli : part prélevée sur le site, part prélevée sur le consolidé null. */
    public record RepartitionDebit(double surSite, double surConsolide) {}

    /**
     * Disponible pour une sortie = solde du site + (si un site est précisé) solde du bucket consolidé null.
     * Quand {@code siteId} est null, le site EST déjà le consolidé : on ne l'additionne pas deux fois.
     */
    public double disponiblePourSortie(String produitId, String siteId) {
        return quantite(produitId, siteId) + (siteId != null ? quantite(produitId, null) : 0.0);
    }

    /** Vérifie que le site + le consolidé couvrent {@code qte}, sinon 422. */
    public void verifierDisponibiliteAvecConsolide(String produitId, String siteId, double qte, String libelleSite) {
        double dispo = disponiblePourSortie(produitId, siteId);
        if (dispo < qte) {
            throw new StockOperationException(
                    "Stock insuffisant" + (libelleSite == null ? "" : " sur " + libelleSite)
                            + " (consolidé inclus) : disponible " + dispo + ", demandé " + qte);
        }
    }

    /**
     * Débite le site source d'abord (min(solde, qte)), puis le reliquat sur le bucket consolidé null.
     * Ne contrôle PAS la disponibilité (à la charge de l'appelant via {@link #verifierDisponibiliteAvecConsolide}).
     * Retourne la répartition effectuée, pour la compensation manuelle.
     */
    public RepartitionDebit debiterAvecRepli(String produitId, String siteId, double qte) {
        double soldeSite = quantite(produitId, siteId);
        double surSite = Math.min(soldeSite, qte);
        double surConsolide = 0.0;
        if (surSite > 0) {
            appliquerDelta(produitId, siteId, -surSite);
        }
        double reliquat = qte - surSite;
        if (reliquat > 0 && siteId != null) {   // siteId==null : le site EST le consolidé, pas de double débit
            appliquerDelta(produitId, null, -reliquat);
            surConsolide = reliquat;
        }
        return new RepartitionDebit(surSite, surConsolide);
    }

    /** Annule un débit en repli (compensation manuelle / rollback). */
    public void recrediterRepli(String produitId, String siteId, RepartitionDebit r) {
        if (r.surSite() > 0) {
            appliquerDelta(produitId, siteId, r.surSite());
        }
        if (r.surConsolide() > 0) {
            appliquerDelta(produitId, null, r.surConsolide());
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
