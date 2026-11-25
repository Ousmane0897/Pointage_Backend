package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DecisionPayload;
import com.example.Pointage_Cleanic.Enum.audit.ActionAudit;
import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Mapper.stockv2.BonEntreeMapper;
import com.example.Pointage_Cleanic.configurations.ExecuteurTransactionnel;
import com.example.Pointage_Cleanic.entities.pieces.Abandon;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.repositories.stockv2.BonEntreeRepository;
import com.example.Pointage_Cleanic.services.evenements.TypesEvenement;
import com.example.Pointage_Cleanic.services.pieces.NumerotationService;
import com.example.Pointage_Cleanic.services.pieces.TraceurPieces;
import com.example.Pointage_Cleanic.services.stockv2.BonSupportService.DemandeurRef;
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

/**
 * Bons d'entrée (7.4), pièces de type {@code BE} depuis le lot 1 / 1b (D4, D5, D6) : chaque
 * transition s'exécute en transaction et y écrit sa ligne d'audit et son événement de domaine ;
 * le numéro n'est attribué qu'à la validation ; un brouillon s'abandonne, il ne se supprime plus.
 * Les notifications (WebSocket, e-mail) partent des consommateurs des événements.
 */
@Service
@RequiredArgsConstructor
public class BonEntreeService {

    private final BonEntreeRepository repository;
    private final BonEntreeMapper mapper;
    private final BonSupportService support;
    private final ReferentielSiteService referentielSite;
    private final MouvementBonGenerator mouvementGenerator;
    private final ExecuteurTransactionnel executeur;
    private final NumerotationService numerotation;
    private final TraceurPieces traceur;
    private final PiecesBon piecesBon;
    private final HabilitationStock habilitation;
    private final MongoTemplate mongoTemplate;

    public PageResponse<BonEntreeDto> list(int page, int size, String q, StatutBon statut, TypeEntree type,
                                           String siteId, LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        Query query = new Query().with(pageable);
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("fournisseur").regex(regex, "i")
            ));
        }
        if (statut != null) {
            query.addCriteria(Criteria.where("statut").is(statut));
        }
        if (type != null) {
            query.addCriteria(Criteria.where("type").is(type));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteDestinationId").is(siteId));
        }
        appliquerDates(query, dateDebut, dateFin);

        List<BonEntree> results = mongoTemplate.find(query, BonEntree.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), BonEntree.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public BonEntreeDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    /**
     * Création en transaction : le bon, sa ligne d'audit et l'événement {@code PIECE_CREEE}
     * ensemble. Pas de numéro en brouillon (D4) ; l'e-mail de création part du consommateur de
     * l'événement, après validation de la transaction.
     */
    public BonEntreeDto creer(BonEntreePayload payload) {
        if (payload.getType() == null) {
            throw new IllegalArgumentException("Le type d'entrée est obligatoire");
        }
        String siteNom = referentielSite.valideEtCharge(payload.getSiteDestinationId()).getNom();
        List<LigneBon> lignes = support.denormaliserLignes(payload.getLignes());
        DemandeurRef demandeur = support.resoudreDemandeur(payload.getDemandeurId());

        return executeur.executer(() -> {
            LocalDateTime now = LocalDateTime.now();
            BonEntree bon = BonEntree.builder()
                    .piece(piecesBon.nouvelle(TypePiece.BE, now))
                    .type(payload.getType())
                    .date(payload.getDate() != null ? payload.getDate() : LocalDate.now())
                    .siteDestinationId(payload.getSiteDestinationId())
                    .siteDestinationNom(siteNom)
                    .fournisseur(payload.getFournisseur())
                    .referenceCommande(payload.getReferenceCommande())
                    .lignes(lignes)
                    .statut(StatutBon.BROUILLON)
                    .demandeurId(demandeur.id())
                    .demandeurNom(demandeur.nom())
                    // Auteur déduit du JWT, jamais accepté du client.
                    .creeParId(support.currentUserId())
                    .creeParEmail(support.currentUserEmail())
                    .creeParNom(support.currentUserNom())
                    .commentaire(payload.getCommentaire())
                    .montantTotal(support.montantTotal(lignes))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            bon.getHistorique().add(support.historique(ActionWorkflow.CREATION, null));
            BonEntree saved = repository.save(bon);
            tracer(TypesEvenement.PIECE_CREEE, ActionAudit.CREATION, null, saved, null);
            return mapper.toDto(saved);
        });
    }

    public BonEntreeDto modifier(String id, BonEntreePayload payload) {
        if (payload.getType() == null) {
            throw new IllegalArgumentException("Le type d'entrée est obligatoire");
        }
        return executeur.executer(() -> {
            BonEntree bon = loadOrThrow(id);
            exigerBrouillon(bon, "modifié");
            habilitation.exigerCreateurOuControleur(bon.getCreeParEmail(), "Cette action");
            String siteNom = referentielSite.valideEtCharge(payload.getSiteDestinationId()).getNom();
            List<LigneBon> lignes = support.denormaliserLignes(payload.getLignes());
            DemandeurRef demandeur = support.resoudreDemandeur(payload.getDemandeurId());
            Map<String, Object> avant = piecesBon.instantane(bon);

            piecesBon.assurer(bon);
            bon.setType(payload.getType());
            bon.setDate(payload.getDate() != null ? payload.getDate() : bon.getDate());
            bon.setSiteDestinationId(payload.getSiteDestinationId());
            bon.setSiteDestinationNom(siteNom);
            bon.setFournisseur(payload.getFournisseur());
            bon.setReferenceCommande(payload.getReferenceCommande());
            bon.setLignes(lignes);
            bon.setDemandeurId(demandeur.id());
            bon.setDemandeurNom(demandeur.nom());
            bon.setCommentaire(payload.getCommentaire());
            bon.setMontantTotal(support.montantTotal(lignes));
            bon.setUpdatedAt(LocalDateTime.now());
            bon.getHistorique().add(support.historique(ActionWorkflow.MODIFICATION, null));
            BonEntree saved = repository.save(bon);
            tracer(TypesEvenement.PIECE_MODIFIEE, ActionAudit.MODIFICATION, avant, saved, null);
            return mapper.toDto(saved);
        });
    }

    /**
     * Abandon d'un brouillon (lot 1 / 1b, D4) : remplace la suppression. Le bon est conservé en
     * statut ABANDONNE, avec l'auteur et la date. Mêmes droits que l'ancienne suppression.
     */
    public BonEntreeDto abandonner(String id) {
        return executeur.executer(() -> {
            BonEntree bon = loadOrThrow(id);
            exigerBrouillon(bon, "abandonné");
            habilitation.exigerCreateurOuControleur(bon.getCreeParEmail(), "Cette action");
            Map<String, Object> avant = piecesBon.instantane(bon);
            LocalDateTime now = LocalDateTime.now();
            piecesBon.assurer(bon).setAbandon(Abandon.builder().par(piecesBon.auteurCourant()).le(now).build());
            bon.setStatut(StatutBon.ABANDONNE);
            bon.setUpdatedAt(now);
            bon.getHistorique().add(support.historique(ActionWorkflow.ABANDON, null));
            BonEntree saved = repository.save(bon);
            tracer(TypesEvenement.PIECE_ABANDONNEE, ActionAudit.ABANDON, avant, saved, null);
            return mapper.toDto(saved);
        });
    }

    /** Transition transactionnelle (lot 1 / 1a, D1) : voir {@link #soumettreDansTransaction}. */
    public BonEntreeDto soumettre(String id) {
        return executeur.executer(() -> soumettreDansTransaction(id));
    }

    private BonEntreeDto soumettreDansTransaction(String id) {
        BonEntree bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.BROUILLON) {
            throw new StockConflitException("Seul un bon en BROUILLON peut être soumis (statut actuel : " + bon.getStatut() + ")");
        }
        habilitation.exigerCreateurOuControleur(bon.getCreeParEmail(), "Cette action");
        Map<String, Object> avant = piecesBon.instantane(bon);
        LocalDateTime now = LocalDateTime.now();
        PieceBase piece = piecesBon.assurer(bon);
        piece.setSoumisePar(piecesBon.auteurCourant());
        piece.setSoumiseLe(now);
        bon.setStatut(StatutBon.SOUMIS);
        bon.setUpdatedAt(now);
        bon.getHistorique().add(support.historique(ActionWorkflow.SOUMISSION, null));
        BonEntree saved = repository.save(bon);
        tracer(TypesEvenement.PIECE_SOUMISE, ActionAudit.SOUMISSION, avant, saved, null);
        return mapper.toDto(saved);
    }

    /** Transition transactionnelle (lot 1 / 1a, D1) : voir {@link #validerDansTransaction}. */
    public BonEntreeDto valider(String id, DecisionPayload decision) {
        return executeur.executer(() -> validerDansTransaction(id, decision));
    }

    /**
     * Validation : numéro attribué (lot 1 / 1b, D4) puis mouvements, soldes et coût, le tout dans
     * la transaction du passage en EFFECTIF. Un échec annule aussi le numéro : ni trou ni doublon.
     */
    private BonEntreeDto validerDansTransaction(String id, DecisionPayload decision) {
        BonEntree bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.SOUMIS) {
            throw new StockConflitException("Seul un bon SOUMIS peut être validé (statut actuel : " + bon.getStatut() + ")");
        }
        // Décision qui engage le stock : super-administrateur seul, comme pour les sorties.
        habilitation.exigerSuperAdmin("Validation");
        String commentaire = decision == null ? null : decision.getCommentaire();
        Map<String, Object> avant = piecesBon.instantane(bon);

        LocalDateTime now = LocalDateTime.now();
        PieceBase piece = piecesBon.assurer(bon);
        bon.setReference(numerotation.numeroter(piece));
        piece.setValideePar(piecesBon.auteurCourant());
        piece.setValideeLe(now);

        // Les mouvements recopient la référence : elle est attribuée avant.
        mouvementGenerator.genererPourEntree(bon);

        bon.setValidateurId(support.currentUserId());
        bon.setValidateurNom(support.currentUserNom());
        bon.setStatut(StatutBon.EFFECTIF);
        bon.setUpdatedAt(now);
        bon.getHistorique().add(support.historique(ActionWorkflow.VALIDATION, commentaire));
        bon.getHistorique().add(support.historique(ActionWorkflow.EFFECTIF, null));
        BonEntree saved = repository.save(bon);
        tracer(TypesEvenement.PIECE_VALIDEE, ActionAudit.VALIDATION, avant, saved, commentaire);
        return mapper.toDto(saved);
    }

    /** Transition transactionnelle (lot 1 / 1a, D1) : voir {@link #refuserDansTransaction}. */
    public BonEntreeDto refuser(String id, DecisionPayload decision) {
        return executeur.executer(() -> refuserDansTransaction(id, decision));
    }

    private BonEntreeDto refuserDansTransaction(String id, DecisionPayload decision) {
        BonEntree bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.SOUMIS) {
            throw new StockConflitException("Seul un bon SOUMIS peut être refusé (statut actuel : " + bon.getStatut() + ")");
        }
        habilitation.exigerSuperAdmin("Refus");
        if (decision == null || decision.getCommentaire() == null || decision.getCommentaire().isBlank()) {
            throw new IllegalArgumentException("Le commentaire de refus est obligatoire");
        }
        String motif = decision.getCommentaire().trim();
        Map<String, Object> avant = piecesBon.instantane(bon);
        piecesBon.assurer(bon);
        bon.setStatut(StatutBon.REFUSE);
        bon.setMotifRefus(motif);
        bon.setValidateurId(support.currentUserId());
        bon.setValidateurNom(support.currentUserNom());
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.REFUS, motif));
        BonEntree saved = repository.save(bon);
        tracer(TypesEvenement.PIECE_REFUSEE, ActionAudit.REFUS, avant, saved, motif);
        return mapper.toDto(saved);
    }

    /** Audit + événement de la transition, dans la transaction courante. */
    private void tracer(String evenement, ActionAudit action, Map<String, Object> avant, BonEntree apres,
                        String motif) {
        traceur.tracer(evenement, action, avant, apres, apres.getStatut().name(), motif, null);
    }

    private BonEntree loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'entrée introuvable : " + id));
    }

    private void exigerBrouillon(BonEntree bon, String action) {
        if (bon.getStatut() != StatutBon.BROUILLON) {
            throw new StockConflitException("Un bon ne peut être " + action + " qu'en statut BROUILLON (statut actuel : " + bon.getStatut() + ")");
        }
    }

    private void appliquerDates(Query query, LocalDate dateDebut, LocalDate dateFin) {
        if (dateDebut != null && dateFin != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut).lte(dateFin));
        } else if (dateDebut != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut));
        } else if (dateFin != null) {
            query.addCriteria(Criteria.where("date").lte(dateFin));
        }
    }
}
