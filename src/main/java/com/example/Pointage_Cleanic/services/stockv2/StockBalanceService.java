package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.exception.StockInsuffisantException;
import com.example.Pointage_Cleanic.repositories.stockv2.StockParSiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Gestion bas-niveau des soldes de stock par couple (produit, site).
 * Source de vérité unique partagée par le catalogue (stock initial),
 * les mouvements et la clôture d'inventaire.
 * <p>
 * Le métier ne gère qu'un seul lieu de stockage : un {@code siteId} null désigne
 * l'{@link #ENTREPOT}, où entre et d'où sort tout le stock. Les sites sont des
 * destinataires de sorties, pas des emplacements. Des soldes rattachés à un site
 * peuvent subsister sur des données historiques (avant la bascule vers l'entrepôt
 * unique) : ils comptent dans le solde consolidé du produit, et les sorties savent
 * les consommer via {@link #debiterAvecRepli}.
 * <p>
 * <b>Écritures atomiques</b> (lot 1 / 1a, décision D1.5). Plus aucune lecture-addition-{@code save} :
 * <ul>
 *   <li>{@link #appliquerDelta} : {@code $inc} atomique en upsert — plus de mise à jour perdue ;</li>
 *   <li>{@link #debiter} : {@code $inc} négatif <b>gardé</b> par {@code quantite >= q} dans le même
 *       {@code findAndModify} — deux sorties simultanées ne peuvent plus passer sous zéro ;
 *       sans document correspondant : {@link StockInsuffisantException} (422) ;</li>
 *   <li>l'unicité (produit, site) est garantie par l'index {@code produit_site_unique} (0103).</li>
 * </ul>
 * Les débits des bons passent par {@link #debiter} / {@link #debiterAvecRepli}. Les chemins hors
 * bons (clôture d'inventaire, suppression définitive, import) gardent {@link #appliquerDelta},
 * non gardé, comme avant : la clôture d'un inventaire global peut aujourd'hui porter l'entrepôt
 * en négatif (voir la migration 0007), et ces chemins sortent du périmètre de 1a (D1.6).
 */
@Service
@RequiredArgsConstructor
public class StockBalanceService {

    /**
     * Le lieu de stockage unique. Tout ce qui entre en stock y est crédité, quel que
     * soit le site de destination documenté sur le bon d'entrée.
     */
    public static final String ENTREPOT = null;

    private final StockParSiteRepository repository;
    private final MongoTemplate mongoTemplate;

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
            throw new StockInsuffisantException(
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

    /**
     * Vérifie que le site + le consolidé couvrent {@code qte}, sinon 422. Contrôle de confort (message
     * précis, cumul par produit) : la garantie vient du débit gardé de {@link #debiter}.
     */
    public void verifierDisponibiliteAvecConsolide(String produitId, String siteId, double qte, String libelleSite) {
        double dispo = disponiblePourSortie(produitId, siteId);
        if (dispo < qte) {
            throw new StockInsuffisantException(
                    "Stock insuffisant" + (libelleSite == null ? "" : " sur " + libelleSite)
                            + " (consolidé inclus) : disponible " + dispo + ", demandé " + qte);
        }
    }

    /**
     * Débite le site source d'abord (min(solde, qte)), puis le reliquat sur le bucket consolidé null,
     * chaque part par un débit <b>gardé</b>. Doit s'exécuter dans une transaction : si la seconde part
     * échoue, la première est annulée avec elle (plus de compensation manuelle).
     */
    public RepartitionDebit debiterAvecRepli(String produitId, String siteId, double qte) {
        exigerTransaction();
        double soldeSite = quantite(produitId, siteId);
        double surSite = Math.max(0.0, Math.min(soldeSite, qte));
        double reliquat = qte - surSite;
        if (siteId == null) {   // le site EST le consolidé : un seul débit, de toute la quantité
            debiter(produitId, null, qte);
            return new RepartitionDebit(qte, 0.0);
        }
        if (surSite > 0) {
            debiter(produitId, siteId, surSite);
        }
        if (reliquat > 0) {
            debiter(produitId, null, reliquat);
        }
        return new RepartitionDebit(surSite, reliquat);
    }

    /**
     * Débit gardé : retire {@code qte} du solde (produit, site) seulement si le solde la couvre, en une
     * seule opération atomique ; sinon {@link StockInsuffisantException} (422), rien n'est écrit.
     */
    public StockParSite debiter(String produitId, String siteId, double qte) {
        Query garde = cle(produitId, siteId).addCriteria(Criteria.where("quantite").gte(qte));
        Update update = new Update().inc("quantite", -qte).set("dateMaj", LocalDateTime.now());
        StockParSite solde = mongoTemplate.findAndModify(garde, update,
                FindAndModifyOptions.options().returnNew(true), StockParSite.class);
        if (solde == null) {
            throw new StockInsuffisantException("Stock insuffisant : disponible " + quantite(produitId, siteId)
                    + ", demandé " + qte);
        }
        return solde;
    }

    /**
     * Applique un delta (positif = entrée, négatif = sortie) au solde du couple (produit, site), par un
     * {@code $inc} atomique en upsert. Retourne le solde après mise à jour. Ne contrôle PAS la
     * disponibilité : pour une sortie, utiliser {@link #debiter}.
     */
    public StockParSite appliquerDelta(String produitId, String siteId, double delta) {
        Update update = new Update().inc("quantite", delta).set("dateMaj", LocalDateTime.now());
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        try {
            return mongoTemplate.findAndModify(cle(produitId, siteId), update, options, StockParSite.class);
        } catch (DuplicateKeyException e) {
            // Deux upserts simultanés du premier solde : l'un crée le document, l'autre heurte
            // l'index unique. Hors transaction, un second essai trouve le document et l'incrémente.
            // Dans une transaction, l'erreur remonte : la transaction entière est rejouée.
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                throw e;
            }
            return mongoTemplate.findAndModify(cle(produitId, siteId), update, options, StockParSite.class);
        }
    }

    /** Couple (produit, site) ; {@code siteId} null désigne l'entrepôt (le champ absent compte aussi). */
    private static Query cle(String produitId, String siteId) {
        return Query.query(Criteria.where("produitId").is(produitId).and("siteId").is(siteId));
    }

    private static void exigerTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Débit en repli hors transaction : passer par ExecuteurTransactionnel");
        }
    }
}
