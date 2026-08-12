package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.LignePayload;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.entities.stockv2.EntreeHistorique;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Briques mutualisées entre BonEntreeService et BonSortieService (workflow identique). */
@Service
@RequiredArgsConstructor
public class BonSupportService {

    /**
     * ⚠ Le super-administrateur est la chaîne {@code SUPERADMIN}, sans underscore : c'est la seule
     * valeur réellement émise (collection {@code login}).
     * <p>
     * Les mêmes littéraux existent dans {@code BonMailNotificationService} et
     * {@code SuppressionDefinitiveService} — à consolider ici lors d'un passage dédié.
     */
    public static final String ROLE_SUPERADMIN = "SUPERADMIN";
    public static final String ROLE_CONTROLEUR_STOCK = "CONTROLEUR_STOCK";

    private final ProduitStockRepository produitRepository;
    private final ReferentielEmployeStockService referentielEmploye;
    private final CurrentUserProvider currentUser;

    /** Demandeur résolu : id éventuel + nom (dénormalisé du dossier RH, ou nom JWT par défaut). */
    public record DemandeurRef(String id, String nom) {
    }

    /**
     * Dénormalise les lignes envoyées (produitId + quantite) en figeant code/libellé/unité/prix
     * et le montant courant. Lève 404 si un produit est introuvable, 400 si quantité <= 0.
     */
    public List<LigneBon> denormaliserLignes(List<LignePayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            throw new IllegalArgumentException("Le bon doit comporter au moins une ligne");
        }
        List<LigneBon> lignes = new ArrayList<>();
        for (LignePayload p : payloads) {
            if (p.getQuantite() <= 0) {
                throw new IllegalArgumentException("La quantité doit être strictement positive");
            }
            ProduitStock produit = produitRepository.findById(p.getProduitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + p.getProduitId()));
            // 7.6 : prix d'achat fourni (entrée) prioritaire, sinon coût courant du produit (7.4/7.5).
            long prixUnitaire = p.getPrixUnitaire() != null ? p.getPrixUnitaire() : produit.getPrixUnitaire();
            long montant = Math.round(p.getQuantite() * prixUnitaire);
            lignes.add(LigneBon.builder()
                    .produitId(produit.getId())
                    .quantite(p.getQuantite())
                    .produitCode(produit.getCode())
                    .produitLibelle(produit.getLibelle())
                    .unite(produit.getUnite())
                    .prixUnitaire(prixUnitaire)
                    .montant(montant)
                    .build());
        }
        return lignes;
    }

    public long montantTotal(List<LigneBon> lignes) {
        return lignes.stream().mapToLong(LigneBon::getMontant).sum();
    }

    /** Résout le demandeur : si un id est fourni, valide et dénormalise ; sinon nom = utilisateur JWT. */
    public DemandeurRef resoudreDemandeur(String demandeurId) {
        if (demandeurId == null || demandeurId.isBlank()) {
            return new DemandeurRef(null, currentUser.currentUserNom());
        }
        String nom = referentielEmploye.nomComplet(referentielEmploye.valideEtCharge(demandeurId).getId());
        return new DemandeurRef(demandeurId, nom);
    }

    public EntreeHistorique historique(ActionWorkflow action, String commentaire) {
        return EntreeHistorique.builder()
                .action(action)
                .auteur(currentUser.currentUserNom())
                .date(LocalDateTime.now())
                .commentaire(commentaire == null || commentaire.isBlank() ? null : commentaire.trim())
                .build();
    }

    public String currentUserId() {
        return currentUser.currentUserId();
    }

    public String currentUserNom() {
        return currentUser.currentUserNom();
    }

    /** Adresse de connexion (subject du JWT) : seul point de comparaison de la propriété d'un bon. */
    public String currentUserEmail() {
        return currentUser.currentEmail();
    }

    /** Rôle courant, en majuscules — voir {@code CurrentUserProvider.currentRole()}. */
    public String currentRole() {
        return currentUser.currentRole();
    }
}
