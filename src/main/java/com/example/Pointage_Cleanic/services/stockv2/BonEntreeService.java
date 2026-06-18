package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreeDto;
import com.example.Pointage_Cleanic.Dto.stockv2.BonEntreePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.DecisionPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.NotificationStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.ActionWorkflow;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Mapper.stockv2.BonEntreeMapper;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.repositories.stockv2.BonEntreeRepository;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BonEntreeService {

    private final BonEntreeRepository repository;
    private final BonEntreeMapper mapper;
    private final BonSupportService support;
    private final ReferentielSiteService referentielSite;
    private final MouvementBonGenerator mouvementGenerator;
    private final StockNotificationService notificationService;
    private final CompteurStockService compteurService;
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

    public BonEntreeDto creer(BonEntreePayload payload) {
        if (payload.getType() == null) {
            throw new IllegalArgumentException("Le type d'entrée est obligatoire");
        }
        String siteNom = referentielSite.valideEtCharge(payload.getSiteDestinationId()).getNom();
        List<LigneBon> lignes = support.denormaliserLignes(payload.getLignes());
        DemandeurRef demandeur = support.resoudreDemandeur(payload.getDemandeurId());

        LocalDateTime now = LocalDateTime.now();
        BonEntree bon = BonEntree.builder()
                .reference(compteurService.genererReference("BE"))
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
                .commentaire(payload.getCommentaire())
                .montantTotal(support.montantTotal(lignes))
                .createdAt(now)
                .updatedAt(now)
                .build();
        bon.getHistorique().add(support.historique(ActionWorkflow.CREATION, null));
        return mapper.toDto(repository.save(bon));
    }

    public BonEntreeDto modifier(String id, BonEntreePayload payload) {
        BonEntree bon = loadOrThrow(id);
        exigerBrouillon(bon, "modifié");
        if (payload.getType() == null) {
            throw new IllegalArgumentException("Le type d'entrée est obligatoire");
        }
        String siteNom = referentielSite.valideEtCharge(payload.getSiteDestinationId()).getNom();
        List<LigneBon> lignes = support.denormaliserLignes(payload.getLignes());
        DemandeurRef demandeur = support.resoudreDemandeur(payload.getDemandeurId());

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
        return mapper.toDto(repository.save(bon));
    }

    public void supprimer(String id) {
        BonEntree bon = loadOrThrow(id);
        exigerBrouillon(bon, "supprimé");
        repository.deleteById(id);
    }

    public BonEntreeDto soumettre(String id) {
        BonEntree bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.BROUILLON) {
            throw new StockConflitException("Seul un bon en BROUILLON peut être soumis (statut actuel : " + bon.getStatut() + ")");
        }
        bon.setStatut(StatutBon.SOUMIS);
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.SOUMISSION, null));
        BonEntree saved = repository.save(bon);
        notificationService.diffuserEtCiblerResponsableAchats(
                notification("BON_SOUMIS", saved, "Bon d'entrée soumis", "Le bon " + saved.getReference() + " attend validation."));
        return mapper.toDto(saved);
    }

    public BonEntreeDto valider(String id, DecisionPayload decision) {
        BonEntree bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.SOUMIS) {
            throw new StockConflitException("Seul un bon SOUMIS peut être validé (statut actuel : " + bon.getStatut() + ")");
        }
        String commentaire = decision == null ? null : decision.getCommentaire();

        // Génère les mouvements AVANT toute mutation persistée (pas de transaction Mongo).
        mouvementGenerator.genererPourEntree(bon);

        bon.setValidateurId(support.currentUserId());
        bon.setValidateurNom(support.currentUserNom());
        bon.setStatut(StatutBon.EFFECTIF);
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.VALIDATION, commentaire));
        bon.getHistorique().add(support.historique(ActionWorkflow.EFFECTIF, null));
        BonEntree saved = repository.save(bon);

        notificationService.diffuser(notification("BON_VALIDE", saved, "Bon d'entrée validé",
                "Le bon " + saved.getReference() + " a été validé."));
        notificationService.diffuser(notification("BON_EFFECTIF", saved, "Stock mis à jour",
                "Le bon " + saved.getReference() + " est effectif : stock mouvementé."));
        return mapper.toDto(saved);
    }

    public BonEntreeDto refuser(String id, DecisionPayload decision) {
        BonEntree bon = loadOrThrow(id);
        if (bon.getStatut() != StatutBon.SOUMIS) {
            throw new StockConflitException("Seul un bon SOUMIS peut être refusé (statut actuel : " + bon.getStatut() + ")");
        }
        if (decision == null || decision.getCommentaire() == null || decision.getCommentaire().isBlank()) {
            throw new IllegalArgumentException("Le commentaire de refus est obligatoire");
        }
        String motif = decision.getCommentaire().trim();
        bon.setStatut(StatutBon.REFUSE);
        bon.setMotifRefus(motif);
        bon.setValidateurId(support.currentUserId());
        bon.setValidateurNom(support.currentUserNom());
        bon.setUpdatedAt(LocalDateTime.now());
        bon.getHistorique().add(support.historique(ActionWorkflow.REFUS, motif));
        BonEntree saved = repository.save(bon);
        notificationService.diffuser(notification("BON_REFUSE", saved, "Bon d'entrée refusé",
                "Le bon " + saved.getReference() + " a été refusé : " + motif));
        return mapper.toDto(saved);
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

    private NotificationStockDto notification(String type, BonEntree bon, String titre, String message) {
        return NotificationStockDto.builder()
                .type(type)
                .sens("ENTREE")
                .bonId(bon.getId())
                .reference(bon.getReference())
                .titre(titre)
                .message(message)
                .dateEmission(LocalDateTime.now().toString())
                .build();
    }
}
