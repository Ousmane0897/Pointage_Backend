package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.entities.stockv2.HistoriquePointCout;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.HistoriquePointCoutRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Socle commun de la valorisation financière (Stock v2 7.6) : résolution de la méthode effective,
 * recalcul du coût courant (CUMP / DERNIER_PRIX / FIXE), snapshot à la création des mouvements et
 * reconstitution du coût pour les mouvements antérieurs à 7.6. Calque sur {@link AnalyseSupport}.
 */
@Component
@RequiredArgsConstructor
public class ValorisationSupport {

    /** Seuils serveur, cohérents avec le frontend figé. */
    public static final double SEUIL_DERIVE = 20.0;
    public static final double SEUIL_DERIVE_CRITIQUE = 40.0;
    public static final double MARGE_MINI = 15.0;
    public static final double ECART_COUT_ANORMAL = 50.0;

    private final ParametrageValorisationService parametrageService;
    private final ProduitStockRepository produitRepository;
    private final HistoriquePointCoutRepository historiqueRepository;

    /** Coût unitaire d'une ligne de mouvement + indicateur d'estimation (reconstitution). */
    public record CoutLigne(long coutUnitaire, boolean estEstime) {
    }

    /** Résultat d'un recalcul d'entrée, porteur des informations de compensation. */
    public record RecalcResult(String produitId, long ancienCout, boolean produitModifie, String historiqueId) {
    }

    /** Méthode effective d'un produit : override produit, sinon méthode globale, sinon FIXE. */
    public MethodeValorisation methodeEffective(ProduitStock produit, MethodeValorisation defautGlobal) {
        if (produit != null && produit.getMethodeValorisation() != null) {
            return produit.getMethodeValorisation();
        }
        return defautGlobal != null ? defautGlobal : MethodeValorisation.FIXE;
    }

    /** Méthode effective en résolvant la méthode globale courante. */
    public MethodeValorisation methodeEffective(ProduitStock produit) {
        return methodeEffective(produit, parametrageService.methodeDefaut());
    }

    /**
     * Calcule le nouveau coût courant lors d'une entrée. Fonction pure (aucune E/S).
     *
     * @param methode      méthode effective du produit
     * @param ancienCout   coût courant avant l'entrée (FCFA)
     * @param stockActuel  stock total avant l'entrée
     * @param q            quantité entrée
     * @param prixAchat    prix d'achat unitaire de l'entrée (FCFA)
     * @return le nouveau coût courant (FCFA), ou {@code ancienCout} si pas de recalcul
     */
    public long recalculerCout(MethodeValorisation methode, long ancienCout,
                               double stockActuel, double q, long prixAchat) {
        if (methode == null) {
            return ancienCout;
        }
        return switch (methode) {
            case FIXE -> ancienCout;
            case DERNIER_PRIX -> Math.round((double) prixAchat);
            case CUMP -> {
                if (q <= 0) {
                    yield ancienCout;
                }
                if (stockActuel + q <= 0) {
                    yield Math.round((double) prixAchat);
                }
                yield Math.round((stockActuel * ancienCout + q * prixAchat) / (stockActuel + q));
            }
        };
    }

    /**
     * Applique le recalcul du coût courant d'un produit à une entrée et historise le point de coût.
     * À appeler APRÈS lecture du stock avant entrée ({@code stockActuel}) et application du delta.
     * Retourne les éléments nécessaires à la compensation manuelle en cas d'échec ultérieur.
     */
    public RecalcResult appliquerEntree(ProduitStock produit, double stockActuel, double q,
                                        long prixAchat, String mvtReference, LocalDate date) {
        long ancienCout = produit.getPrixUnitaire();
        MethodeValorisation methode = methodeEffective(produit);
        long nouveauCout = recalculerCout(methode, ancienCout, stockActuel, q, prixAchat);

        if (methode == MethodeValorisation.FIXE || nouveauCout == ancienCout) {
            return new RecalcResult(produit.getId(), ancienCout, false, null);
        }

        produit.setPrixUnitaire(nouveauCout);
        produit.setUpdatedAt(LocalDateTime.now());
        produitRepository.save(produit);

        HistoriquePointCout point = historiqueRepository.save(HistoriquePointCout.builder()
                .produitId(produit.getId())
                .date(date != null ? date : LocalDate.now())
                .cout(nouveauCout)
                .methode(methode)
                .referenceMouvement(mvtReference)
                .createdAt(LocalDateTime.now())
                .build());

        return new RecalcResult(produit.getId(), ancienCout, true, point.getId());
    }

    /** Compense un recalcul : restaure l'ancien coût du produit et supprime le point d'historique. */
    public void compenserEntree(RecalcResult result) {
        if (result == null || !result.produitModifie()) {
            return;
        }
        produitRepository.findById(result.produitId()).ifPresent(p -> {
            p.setPrixUnitaire(result.ancienCout());
            p.setUpdatedAt(LocalDateTime.now());
            produitRepository.save(p);
        });
        if (result.historiqueId() != null) {
            historiqueRepository.deleteById(result.historiqueId());
        }
    }

    /**
     * Coût unitaire d'une ligne de mouvement pour les vues financières : snapshot gelé s'il existe,
     * sinon coût courant du produit reconstitué (ligne marquée estimée).
     */
    public CoutLigne coutDe(MouvementStock m, Map<String, ProduitStock> produits) {
        if (m.getCoutUnitaireSnapshot() != null) {
            return new CoutLigne(m.getCoutUnitaireSnapshot(), false);
        }
        ProduitStock p = produits.get(m.getProduitId());
        long cout = p == null ? 0L : p.getPrixUnitaire();
        return new CoutLigne(cout, true);
    }
}
