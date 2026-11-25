package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonSortieDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonSortiePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DecisionPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DestinatairePayload;
import com.example.Pointage_Cleanic.Enum.audit.ActionAudit;
import com.example.Pointage_Cleanic.Enum.pieces.TypePiece;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutChantier;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Mapper.stockv2.BonSortieMapper;
import com.example.Pointage_Cleanic.entities.pieces.Abandon;
import com.example.Pointage_Cleanic.entities.pieces.PieceBase;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.Chantier;
import com.example.Pointage_Cleanic.entities.stockv2.DestinataireBon;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.StockAccesRefuseException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.repositories.stockv2.BonSortieRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ChantierRepository;
import com.example.Pointage_Cleanic.services.evenements.TypesEvenement;
import com.example.Pointage_Cleanic.services.pieces.NumerotationService;
import com.example.Pointage_Cleanic.services.pieces.TraceurPieces;
import com.example.Pointage_Cleanic.services.stockv2.BonSupportService.DemandeurRef;

import com.example.Pointage_Cleanic.util.PageResponse;
import com.example.Pointage_Cleanic.configurations.ExecuteurTransactionnel;
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
 * Bons de sortie (7.4, 7.5), pièces de type {@code BS} depuis le lot 1 / 1b (D4, D5, D6) : chaque
 * transition s'exécute en transaction et y écrit sa ligne d'audit et son événement de domaine ;
 * le numéro n'est attribué qu'à la validation ; un brouillon s'abandonne, il ne se supprime plus.
 * Les notifications (WebSocket, e-mail) partent des consommateurs des événements.
 */
@Service
@RequiredArgsConstructor
public class BonSortieService {

    private final BonSortieRepository repository;
    private final BonSortieMapper mapper;
    private final BonSupportService support;
    private final ReferentielSiteService referentielSite;
    private final ReferentielEmployeStockService referentielEmploye;
    private final MouvementBonGenerator mouvementGenerator;
    private final ExecuteurTransactionnel executeur;
    private final NumerotationService numerotation;
    private final TraceurPieces traceur;
    private final PiecesBon piecesBon;
    private final ChantierRepository chantierRepository;
    private final MongoTemplate mongoTemplate;
    private final HabilitationStock habilitation;

    public PageResponse<BonSortieDto> list(int page, int size, String q, StatutBon statut, TypeSortie type,
                                           String siteId, LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        Query query = new Query().with(pageable);
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("motif").regex(regex, "i"),
                    Criteria.where("destinataire.clientNom").regex(regex, "i"),
                    // Seul moyen de retrouver un bon dont le site est hors référentiel : le filtre
                    // siteId ne peut pas le cibler.
                    Criteria.where("siteSourceNom").regex(regex, "i")
            ));
        }
        if (statut != null) {
            query.addCriteria(Criteria.where("statut").is(statut));
        }
        if (type != null) {
            query.addCriteria(Criteria.where("type").is(type));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteSourceId").is(siteId));
        }
        appliquerDates(query, dateDebut, dateFin);

        List<BonSortie> results = mongoTemplate.find(query, BonSortie.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), BonSortie.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public BonSortieDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public BonSortieDto creer(BonSortiePayload payload) {
        if (payload.getType() == null) {
            throw new IllegalArgumentException("Le type de sortie est obligatoire");
        }
        SiteSource site = resoudreSiteSource(payload);
        DestinataireBon destinataire = resoudreDestinataire(payload.getDestinataire());
        List<LigneBon> lignes = support.denormaliserLignes(payload.getLignes());
        DemandeurRef demandeur = support.resoudreDemandeur(payload.getDemandeurId());
        SpecificiteSortie spec = resoudreSpecificite(payload);

        return executeur.executer(() -> {
            LocalDateTime now = LocalDateTime.now();
            BonSortie bon = BonSortie.builder()
                    .piece(piecesBon.nouvelle(TypePiece.BS, now))
                    .type(payload.getType())
                    .date(payload.getDate() != null ? payload.getDate() : LocalDate.now())
                    .siteSourceId(site.id())
                    .siteSourceNom(site.nom())
                    .destinataire(destinataire)
                    .motif(payload.getMotif())
                    .natureDon(spec.natureDon())
                    .beneficiaireDon(spec.beneficiaireDon())
                    .chantierId(spec.chantierId())
                    .chantierReference(spec.chantierReference())
                    .lignes(lignes)
                    .statut(StatutBon.BROUILLON)
                    .demandeurId(demandeur.id())
                    .demandeurNom(demandeur.nom())
                    // Auteur déduit du JWT, jamais accepté du client : c'est lui qui gouverne les
                    // habilitations, contrairement au demandeur choisi dans le formulaire.
                    .creeParId(support.currentUserId())
                    .creeParEmail(support.currentUserEmail())
                    .creeParNom(support.currentUserNom())
                    .commentaire(payload.getCommentaire())
                    .montantTotal(support.montantTotal(lignes))
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            bon.getHistorique().add(support.historique(ActionWorkflow.CREATION, null));
            BonSortie saved = repository.save(bon);
            tracer(TypesEvenement.PIECE_CREEE, ActionAudit.CREATION, null, saved, null);
            return mapper.toDto(saved);
        });
    }

    public BonSortieDto modifier(String id, BonSortiePayload payload) {
        if (payload.getType() == null) {
            throw new IllegalArgumentException("Le type de sortie est obligatoire");
        }
        return executeur.executer(() -> {
            BonSortie bon = loadOrThrow(id);
            exigerBrouillon(bon, "modifié");
            habilitation.exigerCreateurOuControleur(bon.getCreeParEmail(), "Cette action");
            Map<String, Object> avant = piecesBon.instantane(bon);
            piecesBon.assurer(bon);
            SiteSource site = resoudreSiteSource(payload);
            DestinataireBon destinataire = resoudreDestinataire(payload.getDestinataire());
            List<LigneBon> lignes = support.denormaliserLignes(payload.getLignes());
            DemandeurRef demandeur = support.resoudreDemandeur(payload.getDemandeurId());
            SpecificiteSortie spec = resoudreSpecificite(payload);

            bon.setType(payload.getType());
            bon.setDate(payload.getDate() != null ? payload.getDate() : bon.getDate());
            bon.setSiteSourceId(site.id());
            bon.setSiteSourceNom(site.nom());
            bon.setDestinataire(destinataire);
            bon.setMotif(payload.getMotif());
            bon.setNatureDon(spec.natureDon());
            bon.setBeneficiaireDon(spec.beneficiaireDon());
            bon.setChantierId(spec.chantierId());
            bon.setChantierReference(spec.chantierReference());
            bon.setLignes(lignes);
            bon.setDemandeurId(demandeur.id());
            bon.setDemandeurNom(demandeur.nom());
            bon.setCommentaire(payload.getCommentaire());
            bon.setMontantTotal(support.montantTotal(lignes));
            bon.setUpdatedAt(LocalDateTime.now());
            bon.getHistorique().add(support.historique(ActionWorkflow.MODIFICATION, null));
            BonSortie saved = repository.save(bon);
            tracer(TypesEvenement.PIECE_MODIFIEE, ActionAudit.MODIFICATION, avant, saved, null);
            return mapper.toDto(saved);
        });
    }

    /**
     * Abandon d'un brouillon (lot 1 / 1b, D4) : remplace la suppression. Le bon est conservé en
     * statut ABANDONNE, avec l'auteur et la date. Mêmes droits que l'ancienne suppression.
     */
    public BonSortieDto abandonner(String id) {
        return executeur.executer(() -> {
            BonSortie bon = loadOrThrow(id);
            exigerBrouillon(bon, "abandonné");
            habilitation.exigerCreateurOuControleur(bon.getCreeParEmail(), "Cette action");
            Map<String, Object> avant = piecesBon.instantane(bon);
            LocalDateTime now = LocalDateTime.now();
            piecesBon.assurer(bon).setAbandon(Abandon.builder().par(piecesBon.auteurCourant()).le(now).build());
            bon.setStatut(StatutBon.ABANDONNE);
            bon.setUpdatedAt(now);
            bon.getHistorique().add(support.historique(ActionWorkflow.ABANDON, null));
            BonSortie saved = repository.save(bon);
            tracer(TypesEvenement.PIECE_ABANDONNEE, ActionAudit.ABANDON, avant, saved, null);
            return mapper.toDto(saved);
        });
    }

    /** Transition transactionnelle (lot 1 / 1a, D1) : voir {@link #soumettreDansTransaction}. */
    public BonSortieDto soumettre(String id) {
        return executeur.executer(() -> soumettreDansTransaction(id));
    }

    private BonSortieDto soumettreDansTransaction(String id) {
        BonSortie bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.BROUILLON) {
            throw new StockConflitException("Seul un bon en BROUILLON peut être soumis (statut actuel : " + bon.getStatut() + ")");
        }
        // Le créateur soumet ses propres bons : sans cela, il corrigerait un bon repris après refus
        // sans pouvoir le renvoyer dans le circuit.
        habilitation.exigerCreateurOuControleur(bon.getCreeParEmail(), "Cette action");
        Map<String, Object> avant = piecesBon.instantane(bon);
        LocalDateTime now = LocalDateTime.now();
        PieceBase piece = piecesBon.assurer(bon);
        piece.setSoumisePar(piecesBon.auteurCourant());
        piece.setSoumiseLe(now);
        bon.setStatut(StatutBon.SOUMIS);
        bon.setUpdatedAt(now);
        bon.getHistorique().add(support.historique(ActionWorkflow.SOUMISSION, null));
        BonSortie saved = repository.save(bon);
        tracer(TypesEvenement.PIECE_SOUMISE, ActionAudit.SOUMISSION, avant, saved, null);
        return mapper.toDto(saved);
    }

    /** Transition transactionnelle (lot 1 / 1a, D1) : voir {@link #validerDansTransaction}. */
    public BonSortieDto valider(String id, DecisionPayload decision) {
        return executeur.executer(() -> validerDansTransaction(id, decision));
    }

    private BonSortieDto validerDansTransaction(String id, DecisionPayload decision) {
        BonSortie bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.SOUMIS) {
            throw new StockConflitException("Seul un bon SOUMIS peut être validé (statut actuel : " + bon.getStatut() + ")");
        }
        // Décision qui engage le stock : super-administrateur seul.
        habilitation.exigerSuperAdmin("Validation");
        String commentaire = decision == null ? null : decision.getCommentaire();

        // 7.5 : un bon DISTRIBUTION_CHANTIER ne peut être validé vers un chantier clôturé.
        if (bon.getType() == TypeSortie.DISTRIBUTION_CHANTIER && bon.getChantierId() != null) {
            Chantier chantier = chantierRepository.findById(bon.getChantierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chantier introuvable : " + bon.getChantierId()));
            if (chantier.getStatut() == StatutChantier.CLOTURE) {
                throw new StockConflitException(
                        "Impossible de valider : le chantier " + chantier.getReference() + " est clôturé");
            }
        }

        Map<String, Object> avant = piecesBon.instantane(bon);
        LocalDateTime now = LocalDateTime.now();
        // Numéro attribué dans la transaction (lot 1 / 1b, D4) : un échec (422, 409) l'annule aussi.
        PieceBase piece = piecesBon.assurer(bon);
        bon.setReference(numerotation.numeroter(piece));
        piece.setValideePar(piecesBon.auteurCourant());
        piece.setValideeLe(now);

        // Mouvements et débits gardés dans la même transaction : 422 (stock insuffisant) annule tout.
        // Les mouvements recopient la référence : elle est attribuée avant.
        mouvementGenerator.genererPourSortie(bon);

        bon.setValidateurId(support.currentUserId());
        bon.setValidateurNom(support.currentUserNom());
        bon.setStatut(StatutBon.EFFECTIF);
        bon.setUpdatedAt(now);
        bon.getHistorique().add(support.historique(ActionWorkflow.VALIDATION, commentaire));
        bon.getHistorique().add(support.historique(ActionWorkflow.EFFECTIF, null));
        BonSortie saved = repository.save(bon);
        tracer(TypesEvenement.PIECE_VALIDEE, ActionAudit.VALIDATION, avant, saved, commentaire);
        return mapper.toDto(saved);
    }

    /** Transition transactionnelle (lot 1 / 1a, D1) : voir {@link #refuserDansTransaction}. */
    public BonSortieDto refuser(String id, DecisionPayload decision) {
        return executeur.executer(() -> refuserDansTransaction(id, decision));
    }

    private BonSortieDto refuserDansTransaction(String id, DecisionPayload decision) {
        BonSortie bon = loadOrThrow(id);
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
        BonSortie saved = repository.save(bon);
        tracer(TypesEvenement.PIECE_REFUSEE, ActionAudit.REFUS, avant, saved, motif);
        return mapper.toDto(saved);
    }

    /**
     * Reprise d'un bon refusé : il repasse en BROUILLON pour que son créateur le corrige, puis le
     * renvoie dans le circuit. {@code REFUSE} cesse ainsi d'être un cul-de-sac.
     *
     * <p>Action explicite plutôt qu'un bon refusé rendu modifiable en place : sans elle, la colonne
     * <i>Refusé</i> du Kanban mélangerait refus définitifs et corrections en cours.
     *
     * <p>⚠ L'historique est <b>augmenté</b> d'une entrée {@code REPRISE}, jamais réinitialisé, et
     * {@code motifRefus} est <b>conservé</b> — écrasé seulement au refus suivant : c'est au moment de
     * corriger que le créateur en a le plus besoin. Le front l'affiche alors en bandeau orange.
     */
    /** Transition transactionnelle (lot 1 / 1a, D1) : voir {@link #reprendreDansTransaction}. */
    public BonSortieDto reprendre(String id) {
        return executeur.executer(() -> reprendreDansTransaction(id));
    }

    private BonSortieDto reprendreDansTransaction(String id) {
        BonSortie bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.REFUSE) {
            throw new StockConflitException(
                    "Seul un bon REFUSE peut être repris (statut actuel : " + bon.getStatut() + ")");
        }
        habilitation.exigerCreateurOuControleur(bon.getCreeParEmail(), "Cette action");

        Map<String, Object> avant = piecesBon.instantane(bon);
        piecesBon.assurer(bon);
        bon.setStatut(StatutBon.BROUILLON);
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.REPRISE, null));
        BonSortie saved = repository.save(bon);
        tracer(TypesEvenement.PIECE_REPRISE, ActionAudit.REPRISE, avant, saved, null);
        return mapper.toDto(saved);
    }


    /** Longueur maximale d'un nom de site saisi hors référentiel. */
    static final int LONGUEUR_MAX_SITE_LIBRE = 120;

    /**
     * Site source du bon : un site du référentiel ({@code siteSourceId}, nom relu en base — celui
     * du client est ignoré) ou, à défaut, un site <b>hors référentiel</b> saisi en texte libre
     * ({@code siteSourceId} nul, nom normalisé). Le stock n'en dépend pas : depuis l'entrepôt
     * unique, une sortie débite l'entrepôt quel que soit le site ; celui-ci n'est qu'une clé
     * d'imputation analytique (cf. {@link SiteSourceCle}).
     */
    private SiteSource resoudreSiteSource(BonSortiePayload payload) {
        String siteId = payload.getSiteSourceId();
        if (siteId != null && !siteId.isBlank()) {
            return new SiteSource(siteId, referentielSite.valideEtCharge(siteId).getNom());
        }
        String nom = SiteSourceCle.normaliserNom(payload.getSiteSourceNom());
        if (nom == null) {
            throw new IllegalArgumentException(
                    "Site source obligatoire : choisissez un site ou saisissez son nom");
        }
        if (nom.length() > LONGUEUR_MAX_SITE_LIBRE) {
            throw new IllegalArgumentException(
                    "Le nom du site ne doit pas dépasser " + LONGUEUR_MAX_SITE_LIBRE + " caractères");
        }
        return new SiteSource(null, nom);
    }

    private record SiteSource(String id, String nom) {
    }

    /** Valide et résout les champs propres au type de sortie (don / chantier). */
    private SpecificiteSortie resoudreSpecificite(BonSortiePayload payload) {
        TypeSortie type = payload.getType();
        if (type == TypeSortie.DON) {
            if (payload.getChantierId() != null && !payload.getChantierId().isBlank()) {
                throw new IllegalArgumentException("Un bon DON ne peut pas être rattaché à un chantier");
            }
            if (payload.getNatureDon() == null) {
                throw new IllegalArgumentException("Bon DON : la nature du don est obligatoire");
            }
            if (payload.getBeneficiaireDon() == null || payload.getBeneficiaireDon().isBlank()) {
                throw new IllegalArgumentException("Bon DON : le bénéficiaire du don est obligatoire");
            }
            return new SpecificiteSortie(payload.getNatureDon(), payload.getBeneficiaireDon().trim(), null, null);
        }
        if (type == TypeSortie.DISTRIBUTION_CHANTIER) {
            if (payload.getNatureDon() != null) {
                throw new IllegalArgumentException("Un bon DISTRIBUTION_CHANTIER ne peut pas porter de nature de don");
            }
            if (payload.getChantierId() == null || payload.getChantierId().isBlank()) {
                throw new IllegalArgumentException("Bon DISTRIBUTION_CHANTIER : le chantier est obligatoire");
            }
            Chantier chantier = chantierRepository.findById(payload.getChantierId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chantier introuvable : " + payload.getChantierId()));
            return new SpecificiteSortie(null, null, chantier.getId(), chantier.getReference());
        }
        return new SpecificiteSortie(null, null, null, null);
    }

    private record SpecificiteSortie(com.example.Pointage_Cleanic.Enum.stockv2.NatureDon natureDon,
                                     String beneficiaireDon, String chantierId, String chantierReference) {
    }

    private DestinataireBon resoudreDestinataire(DestinatairePayload payload) {
        if (payload == null || payload.getType() == null) {
            throw new IllegalArgumentException("Le destinataire (type) est obligatoire");
        }
        DestinataireBon.DestinataireBonBuilder builder = DestinataireBon.builder().type(payload.getType());
        switch (payload.getType()) {
            case SITE -> {
                if (payload.getSiteId() == null || payload.getSiteId().isBlank()) {
                    throw new IllegalArgumentException("Destinataire SITE : siteId obligatoire");
                }
                builder.siteId(payload.getSiteId())
                        .siteNom(referentielSite.valideEtCharge(payload.getSiteId()).getNom());
            }
            case AGENT -> {
                if (payload.getAgentId() == null || payload.getAgentId().isBlank()) {
                    throw new IllegalArgumentException("Destinataire AGENT : agentId obligatoire");
                }
                builder.agentId(payload.getAgentId())
                        .agentNom(ReferentielEmployeStockService.nomComplet(
                                referentielEmploye.valideEtCharge(payload.getAgentId())));
            }
            case CLIENT -> {
                if (payload.getClientNom() == null || payload.getClientNom().isBlank()) {
                    throw new IllegalArgumentException("Destinataire CLIENT : clientNom obligatoire");
                }
                builder.clientNom(payload.getClientNom().trim());
            }
        }
        return builder.build();
    }

    private BonSortie loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bon de sortie introuvable : " + id));
    }

    private void exigerBrouillon(BonSortie bon, String action) {
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

    /**
     * Annulation d'un bon EFFECTIF (lot 1 / 1b, D4) : remplace la suppression définitive. Le bon
     * reste en base (statut ANNULE, qui, quand, motif) ; ses mouvements sont contre-passés (stock
     * recrédité) par le consommateur de l'événement {@code PIECE_ANNULEE}. Super-administrateur
     * seul, motif de 10 caractères minimum (400), autre statut 409.
     */
    public BonSortieDto annuler(String id, String motif) {
        String motifNet = PiecesBon.exigerMotifAnnulation(motif);
        return executeur.executer(() -> {
            BonSortie bon = loadOrThrow(id);
            if (bon.getStatut() != StatutBon.EFFECTIF) {
                throw new StockConflitException("Seul un bon EFFECTIF peut être annulé (statut actuel : " + bon.getStatut() + ")");
            }
            habilitation.exigerSuperAdmin("Annulation");
            Map<String, Object> avant = piecesBon.instantane(bon);
            LocalDateTime now = LocalDateTime.now();
            piecesBon.assurer(bon).setAnnulation(piecesBon.annulation(motifNet, now));
            bon.setStatut(StatutBon.ANNULE);
            bon.setUpdatedAt(now);
            bon.getHistorique().add(support.historique(ActionWorkflow.ANNULATION, motifNet));
            BonSortie saved = repository.save(bon);
            tracer(TypesEvenement.PIECE_ANNULEE, ActionAudit.ANNULATION, avant, saved, motifNet);
            return mapper.toDto(saved);
        });
    }

    /** Audit + événement de la transition, dans la transaction courante. */
    private void tracer(String evenement, ActionAudit action, Map<String, Object> avant, BonSortie apres,
                        String motif) {
        traceur.tracer(evenement, action, avant, apres, apres.getStatut().name(), motif, null);
    }
}
