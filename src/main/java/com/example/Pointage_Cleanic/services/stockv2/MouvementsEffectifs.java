package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Mouvements qui comptent dans les <b>consommations et les coûts</b> (lot 1 / 1b, D4) : ni un
 * mouvement contre-passé (pièce annulée), ni un mouvement d'annulation. Les rejeux de quantités
 * et de valeur du stock gardent au contraire tous les mouvements : l'aller et le retour s'y
 * annulent.
 */
public final class MouvementsEffectifs {

    /** Origine d'un mouvement inverse produit par l'annulation d'une pièce. */
    public static final String ORIGINE_ANNULATION = "ANNULATION";

    private MouvementsEffectifs() {
    }

    /** Ajoute le critère à une requête de consommation ou de coût. */
    public static Query exclureAnnulations(Query query) {
        query.addCriteria(Criteria.where("origine").ne(ORIGINE_ANNULATION));
        return exclureContrePasses(query);
    }

    /**
     * Seulement l'exclusion des mouvements contre-passés : pour une requête qui fixe déjà
     * {@code origine} (ex. {@code origine = "BON"}, qui écarte de fait les mouvements
     * d'annulation). Une requête Mongo n'accepte qu'un critère par champ.
     */
    public static Query exclureContrePasses(Query query) {
        return query.addCriteria(Criteria.where("contrePasse").ne(true));
    }

    /** Même critère, en mémoire (mouvements lus par un repository). */
    public static boolean compte(MouvementStock m) {
        return !Boolean.TRUE.equals(m.getContrePasse()) && !ORIGINE_ANNULATION.equals(m.getOrigine());
    }
}
