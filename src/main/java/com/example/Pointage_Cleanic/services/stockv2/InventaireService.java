package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComptagePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.InventaireDto;
import com.example.Pointage_Cleanic.Dto.stockv2.InventairePlanifPayload;
import com.example.Pointage_Cleanic.Enum.audit.ActionAudit;
import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Mapper.stockv2.InventaireMapper;
import com.example.Pointage_Cleanic.configurations.ExecuteurTransactionnel;
import com.example.Pointage_Cleanic.entities.pieces.Abandon;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import com.example.Pointage_Cleanic.entities.stockv2.Inventaire;
import com.example.Pointage_Cleanic.entities.stockv2.LigneInventaire;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.exception.StockOperationException;
import com.example.Pointage_Cleanic.repositories.stockv2.InventaireRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.services.evenements.TypesEvenement;
import com.example.Pointage_Cleanic.services.pieces.NumerotationService;
import com.example.Pointage_Cleanic.services.pieces.TraceurPieces;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Inventaires (7.3), pièces de type {@code INV} depuis le lot 1 / 1b (D4, D5, D6) : chaque
 * transition s'exécute en transaction et y écrit sa ligne d'audit et son événement ; le numéro
 * est attribué à la clôture ; un brouillon s'abandonne et un inventaire clôturé s'annule
 * (ajustements contre-passés par événement), plus rien ne se supprime.
 */
@Service
@RequiredArgsConstructor
public class InventaireService {

    private final InventaireRepository repository;
    private final InventaireMapper mapper;
    private final ProduitStockRepository produitRepository;
    private final StockBalanceService balanceService;
    private final MouvementStockRepository mouvementRepository;
    private final CompteurStockService compteurService;
    private final ReferentielSiteService referentielSite;
    private final CurrentUserProvider currentUser;
    private final MongoTemplate mongoTemplate;
    private final HabilitationStock habilitation;
    private final ExecuteurTransactionnel executeur;
    private final NumerotationService numerotation;
    private final TraceurPieces traceur;
    private final PiecesBon piecesBon;
    private final ContrePassementStockService contrePassement;

    public PageResponse<InventaireDto> list(int page, int size, String q, StatutInventaire statut,
                                            String siteId, LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("datePlanifiee").descending());
        Query query = new Query().with(pageable);
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("libelle").regex(regex, "i")
            ));
        }
        if (statut != null) {
            query.addCriteria(Criteria.where("statut").is(statut));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteId").is(siteId));
        }
        if (dateDebut != null && dateFin != null) {
            query.addCriteria(Criteria.where("datePlanifiee").gte(dateDebut).lte(dateFin));
        } else if (dateDebut != null) {
            query.addCriteria(Criteria.where("datePlanifiee").gte(dateDebut));
        } else if (dateFin != null) {
            query.addCriteria(Criteria.where("datePlanifiee").lte(dateFin));
        }

        List<Inventaire> results = mongoTemplate.find(query, Inventaire.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Inventaire.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public InventaireDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public InventaireDto create(InventairePlanifPayload payload) {
        validerPlanif(payload);
        String siteNom = referentielSite.nomDuSite(blankToNull(payload.getSiteId()));
        List<LigneInventaire> lignes = construireLignes(payload);
        return executeur.executer(() -> {
            LocalDateTime now = LocalDateTime.now();
            Inventaire inv = Inventaire.builder()
                    .piece(piecesBon.nouvelle(TypePiece.INV, now))
                    .libelle(payload.getLibelle())
                    .datePlanifiee(payload.getDatePlanifiee())
                    .siteId(blankToNull(payload.getSiteId()))
                    .siteNom(siteNom)
                    .perimetre(payload.getPerimetre())
                    .categorieId(blankToNull(payload.getCategorieId()))
                    .seuilEcartJustification(payload.getSeuilEcartJustification())
                    .statut(StatutInventaire.BROUILLON)
                    .lignes(lignes)
                    .responsable(currentUser.currentUserNom())
                    .commentaire(payload.getCommentaire())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            Inventaire saved = repository.save(inv);
            tracer(TypesEvenement.PIECE_CREEE, ActionAudit.CREATION, null, saved, null);
            return mapper.toDto(saved);
        });
    }

    public InventaireDto update(String id, InventairePlanifPayload payload) {
        validerPlanif(payload);
        return executeur.executer(() -> {
            Inventaire inv = loadOrThrow(id);
            if (inv.getStatut() != StatutInventaire.BROUILLON) {
                throw new StockConflitException("Modification interdite : l'inventaire n'est plus en BROUILLON");
            }
            Map<String, Object> avant = piecesBon.instantane(inv);
            piecesBon.assurer(inv);
            inv.setLibelle(payload.getLibelle());
            inv.setDatePlanifiee(payload.getDatePlanifiee());
            inv.setSiteId(blankToNull(payload.getSiteId()));
            inv.setSiteNom(referentielSite.nomDuSite(blankToNull(payload.getSiteId())));
            inv.setPerimetre(payload.getPerimetre());
            inv.setCategorieId(blankToNull(payload.getCategorieId()));
            inv.setSeuilEcartJustification(payload.getSeuilEcartJustification());
            inv.setLignes(construireLignes(payload));
            inv.setCommentaire(payload.getCommentaire());
            inv.setUpdatedAt(LocalDateTime.now());
            Inventaire saved = repository.save(inv);
            tracer(TypesEvenement.PIECE_MODIFIEE, ActionAudit.MODIFICATION, avant, saved, null);
            return mapper.toDto(saved);
        });
    }

    /**
     * Abandon d'un brouillon (lot 1 / 1b, D4) : remplace la suppression. L'inventaire est conservé
     * en statut ABANDONNE, avec l'auteur et la date.
     */
    public InventaireDto abandonner(String id) {
        return executeur.executer(() -> {
            Inventaire inv = loadOrThrow(id);
            if (inv.getStatut() != StatutInventaire.BROUILLON) {
                throw new StockConflitException("Abandon interdit : l'inventaire n'est plus en BROUILLON");
            }
            Map<String, Object> avant = piecesBon.instantane(inv);
            LocalDateTime now = LocalDateTime.now();
            piecesBon.assurer(inv).setAbandon(Abandon.builder().par(piecesBon.auteurCourant()).le(now).build());
            inv.setStatut(StatutInventaire.ABANDONNE);
            inv.setUpdatedAt(now);
            Inventaire saved = repository.save(inv);
            tracer(TypesEvenement.PIECE_ABANDONNEE, ActionAudit.ABANDON, avant, saved, null);
            return mapper.toDto(saved);
        });
    }

    /** BROUILLON -> COMPTAGE : fige qteTheorique au stock système courant de chaque ligne. */
    public InventaireDto demarrerComptage(String id) {
        return executeur.executer(() -> {
            Inventaire inv = loadOrThrow(id);
            if (inv.getStatut() != StatutInventaire.BROUILLON) {
                throw new StockConflitException("Comptage impossible depuis le statut " + inv.getStatut());
            }
            Map<String, Object> avant = piecesBon.instantane(inv);
            for (LigneInventaire ligne : inv.getLignes()) {
                ligne.setQteTheorique(stockSysteme(ligne.getProduitId(), inv.getSiteId()));
                if (ligne.getQtePhysique() != null) {
                    ligne.setEcart(ligne.getQtePhysique() - ligne.getQteTheorique());
                }
            }
            LocalDateTime now = LocalDateTime.now();
            PieceBase piece = piecesBon.assurer(inv);
            piece.setSoumisePar(piecesBon.auteurCourant());
            piece.setSoumiseLe(now);
            inv.setStatut(StatutInventaire.COMPTAGE);
            inv.setUpdatedAt(now);
            Inventaire saved = repository.save(inv);
            tracer(TypesEvenement.PIECE_SOUMISE, ActionAudit.SOUMISSION, avant, saved, null);
            return mapper.toDto(saved);
        });
    }

    /** Enregistre qtePhysique + justification, recalcule ecart ; reste en COMPTAGE. */
    public InventaireDto enregistrerComptage(String id, ComptagePayload payload) {
        return executeur.executer(() -> {
            Inventaire inv = loadOrThrow(id);
            if (inv.getStatut() != StatutInventaire.COMPTAGE) {
                throw new StockConflitException("Saisie de comptage impossible depuis le statut " + inv.getStatut());
            }
            Map<String, Object> avant = piecesBon.instantane(inv);
            Map<String, ComptagePayload.LigneComptage> saisies = payload == null || payload.getLignes() == null
                    ? Map.of()
                    : payload.getLignes().stream()
                    .filter(l -> l.getProduitId() != null)
                    .collect(Collectors.toMap(ComptagePayload.LigneComptage::getProduitId, l -> l, (a, b) -> b));

            for (LigneInventaire ligne : inv.getLignes()) {
                ComptagePayload.LigneComptage saisie = saisies.get(ligne.getProduitId());
                if (saisie == null) {
                    continue;
                }
                ligne.setQtePhysique(saisie.getQtePhysique());
                ligne.setJustification(saisie.getJustification());
                ligne.setEcart(saisie.getQtePhysique() == null ? null : saisie.getQtePhysique() - ligne.getQteTheorique());
            }
            piecesBon.assurer(inv);
            inv.setUpdatedAt(LocalDateTime.now());
            Inventaire saved = repository.save(inv);
            tracer(TypesEvenement.PIECE_MODIFIEE, ActionAudit.MODIFICATION, avant, saved, null);
            return mapper.toDto(saved);
        });
    }

    /** COMPTAGE -> VALIDATION : refuse en 422 si un écart > seuil n'est pas justifié. */
    public InventaireDto valider(String id) {
        return executeur.executer(() -> {
            Inventaire inv = loadOrThrow(id);
            if (inv.getStatut() != StatutInventaire.COMPTAGE) {
                throw new StockConflitException("Validation impossible depuis le statut " + inv.getStatut());
            }
            for (LigneInventaire ligne : inv.getLignes()) {
                double ecart = ligne.getEcart() == null ? 0.0 : ligne.getEcart();
                boolean justifiee = ligne.getJustification() != null && !ligne.getJustification().isBlank();
                if (Math.abs(ecart) > inv.getSeuilEcartJustification() && !justifiee) {
                    throw new StockOperationException(
                            "Écart non justifié sur le produit " + ligne.getProduitCode()
                                    + " (écart " + ecart + " > seuil " + inv.getSeuilEcartJustification() + ")");
                }
            }
            Map<String, Object> avant = piecesBon.instantane(inv);
            piecesBon.assurer(inv);
            inv.setStatut(StatutInventaire.VALIDATION);
            inv.setUpdatedAt(LocalDateTime.now());
            Inventaire saved = repository.save(inv);
            tracer(TypesEvenement.PIECE_MODIFIEE, ActionAudit.MODIFICATION, avant, saved, null);
            return mapper.toDto(saved);
        });
    }

    /**
     * VALIDATION -> CLOTURE : applique les écarts au stock via des mouvements AJUSTEMENT.
     *
     * <p>Lot 1 / 1b : en transaction (numéro {@code INV-2026-0001}, mouvements, soldes, statut,
     * audit et événement ensemble ou pas du tout) ; la compensation manuelle est retirée (D1.6).
     */
    public InventaireDto cloturer(String id) {
        return executeur.executer(() -> cloturerDansTransaction(id));
    }

    private InventaireDto cloturerDansTransaction(String id) {
        Inventaire inv = loadOrThrow(id);
        if (inv.getStatut() != StatutInventaire.VALIDATION) {
            throw new StockConflitException("Clôture impossible depuis le statut " + inv.getStatut());
        }
        // Seule étape irréversible du workflow : elle applique les écarts au stock réel.
        habilitation.exigerControleurOuSuperAdmin("La clôture d'un inventaire est");
        Map<String, Object> avant = piecesBon.instantane(inv);
        String utilisateur = currentUser.currentUserNom();
        LocalDateTime now = LocalDateTime.now();

        // Numéro attribué dans la transaction : les ajustements le recopient.
        PieceBase piece = piecesBon.assurer(inv);
        inv.setReference(numerotation.numeroter(piece));
        piece.setValideePar(piecesBon.auteurCourant());
        piece.setValideeLe(now);

        for (LigneInventaire ligne : inv.getLignes()) {
            double ecart = ligne.getEcart() == null ? 0.0 : ligne.getEcart();
            if (ecart == 0.0) {
                continue;
            }
            ProduitStock produit = produitRepository.findById(ligne.getProduitId()).orElse(null);
            String siteId = inv.getSiteId();
            MouvementStock mvt = MouvementStock.builder()
                    .reference(compteurService.genererReference("MVT"))
                    .produitId(ligne.getProduitId())
                    .produitCode(ligne.getProduitCode())
                    .produitLibelle(ligne.getProduitLibelle())
                    .unite(ligne.getUnite())
                    .type(ecart > 0 ? TypeMouvement.ENTREE : TypeMouvement.SORTIE)
                    .motif(MotifMouvement.AJUSTEMENT)
                    .quantite(Math.abs(ecart))
                    .siteDestinationId(ecart > 0 ? siteId : null)
                    .siteDestinationNom(ecart > 0 ? inv.getSiteNom() : null)
                    .siteSourceId(ecart < 0 ? siteId : null)
                    .siteSourceNom(ecart < 0 ? inv.getSiteNom() : null)
                    .date(LocalDate.now())
                    .utilisateur(utilisateur)
                    .commentaire("Ajustement inventaire " + inv.getReference())
                    .createdAt(now)
                    // Rattachement explicite : le commentaire était le seul lien vers l'inventaire,
                    // ce qui ne permettait pas de retrouver ces ajustements de façon fiable.
                    .origine("INVENTAIRE")
                    .inventaireId(inv.getId())
                    .inventaireReference(inv.getReference())
                    .build();
            mouvementRepository.save(mvt);
            // Non gardé, comme avant 1b : un inventaire global peut porter l'entrepôt en négatif.
            balanceService.appliquerDelta(ligne.getProduitId(), siteId, ecart);
            if (produit != null) {
                produit.setUpdatedAt(now);
                produitRepository.save(produit);
            }
        }

        inv.setStatut(StatutInventaire.CLOTURE);
        inv.setDateCloture(LocalDate.now());
        inv.setUpdatedAt(now);
        Inventaire saved = repository.save(inv);
        tracer(TypesEvenement.PIECE_VALIDEE, ActionAudit.VALIDATION, avant, saved, null);
        return mapper.toDto(saved);
    }

    /**
     * Annulation d'un inventaire clôturé (lot 1 / 1b, D4) : remplace la suppression définitive.
     * L'inventaire reste en base (statut ANNULE, qui, quand, motif) ; ses ajustements sont
     * contre-passés <b>dans la même transaction</b>, comme les bons (D5). Super-administrateur,
     * motif de 10 caractères minimum (400), autre statut 409.
     */
    public InventaireDto annuler(String id, String motif) {
        String motifNet = PiecesBon.exigerMotifAnnulation(motif);
        return executeur.executer(() -> {
            Inventaire inv = loadOrThrow(id);
            if (inv.getStatut() != StatutInventaire.CLOTURE) {
                throw new StockConflitException("Seul un inventaire CLOTURE peut être annulé (statut actuel : " + inv.getStatut() + ")");
            }
            habilitation.exigerSuperAdmin("Annulation");
            Map<String, Object> avant = piecesBon.instantane(inv);
            LocalDateTime now = LocalDateTime.now();
            piecesBon.assurer(inv).setAnnulation(piecesBon.annulation(motifNet, now));
            inv.setStatut(StatutInventaire.ANNULE);
            inv.setUpdatedAt(now);
            Inventaire saved = repository.save(inv);
            contrePassement.contrePasserInventaire(saved.getId(), saved.getReference(), motifNet,
                    piecesBon.auteurCourant().getNom());
            tracer(TypesEvenement.PIECE_ANNULEE, ActionAudit.ANNULATION, avant, saved, motifNet);
            return mapper.toDto(saved);
        });
    }

    /** Audit + événement de la transition, dans la transaction courante. */
    private void tracer(String evenement, ActionAudit action, Map<String, Object> avant, Inventaire apres,
                        String motif) {
        traceur.tracer(evenement, action, avant, apres, apres.getStatut().name(), motif, null);
    }

    public Inventaire loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventaire introuvable : " + id));
    }

    // ---------------------------------------------------------------- helpers

    private void validerPlanif(InventairePlanifPayload payload) {
        if (payload.getPerimetre() == null) {
            throw new IllegalArgumentException("Le périmètre de l'inventaire est obligatoire");
        }
        if (payload.getPerimetre() == PerimetreInventaire.CATEGORIE
                && (payload.getCategorieId() == null || payload.getCategorieId().isBlank())) {
            throw new IllegalArgumentException("Périmètre CATEGORIE : categorieId obligatoire");
        }
        if (payload.getPerimetre() == PerimetreInventaire.SELECTION
                && (payload.getProduitIds() == null || payload.getProduitIds().isEmpty())) {
            throw new IllegalArgumentException("Périmètre SELECTION : produitIds obligatoire");
        }
    }

    private List<LigneInventaire> construireLignes(InventairePlanifPayload payload) {
        List<ProduitStock> produits = switch (payload.getPerimetre()) {
            case TOUS -> produitRepository.findAll();
            case CATEGORIE -> produitRepository.findByCategorieId(payload.getCategorieId());
            case SELECTION -> produitRepository.findAllById(payload.getProduitIds());
        };
        return produits.stream().map(p -> LigneInventaire.builder()
                        .produitId(p.getId())
                        .produitCode(p.getCode())
                        .produitLibelle(p.getLibelle())
                        .unite(p.getUnite())
                        .qteTheorique(0.0)
                        .qtePhysique(null)
                        .ecart(null)
                        .build())
                .collect(Collectors.toList());
    }

    private double stockSysteme(String produitId, String siteId) {
        return siteId == null ? balanceService.quantiteTotale(produitId) : balanceService.quantite(produitId, siteId);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
