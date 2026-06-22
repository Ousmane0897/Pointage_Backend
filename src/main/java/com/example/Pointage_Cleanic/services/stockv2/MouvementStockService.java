package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.MouvementPayload;
import com.example.Pointage_Cleanic.Dto.stockv2.MouvementStockDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Mapper.stockv2.MouvementStockMapper;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MouvementStockService {

    private final MouvementStockRepository repository;
    private final MouvementStockMapper mapper;
    private final ProduitStockRepository produitRepository;
    private final StockBalanceService balanceService;
    private final CompteurStockService compteurService;
    private final CurrentUserProvider currentUser;
    private final MongoTemplate mongoTemplate;

    public PageResponse<MouvementStockDto> list(int page, int size, String q, String produitId,
                                                TypeMouvement type, MotifMouvement motif, String siteId,
                                                LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        Query query = new Query().with(pageable);

        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("reference").regex(regex, "i"),
                    Criteria.where("produitCode").regex(regex, "i"),
                    Criteria.where("produitLibelle").regex(regex, "i")
            ));
        }
        if (produitId != null && !produitId.isBlank()) {
            query.addCriteria(Criteria.where("produitId").is(produitId));
        }
        if (type != null) {
            query.addCriteria(Criteria.where("type").is(type));
        }
        if (motif != null) {
            query.addCriteria(Criteria.where("motif").is(motif));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("siteSourceId").is(siteId),
                    Criteria.where("siteDestinationId").is(siteId)
            ));
        }
        if (dateDebut != null && dateFin != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut).lte(dateFin));
        } else if (dateDebut != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut));
        } else if (dateFin != null) {
            query.addCriteria(Criteria.where("date").lte(dateFin));
        }

        List<MouvementStock> results = mongoTemplate.find(query, MouvementStock.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), MouvementStock.class);
        List<MouvementStockDto> content = results.stream().map(mapper::toDto).toList();
        return new PageResponse<>(content, total);
    }

    public MouvementStockDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public MouvementStockDto create(MouvementPayload payload) {
        if (payload.getType() == null) {
            throw new IllegalArgumentException("Le type de mouvement est obligatoire");
        }
        if (payload.getMotif() == null) {
            throw new IllegalArgumentException("Le motif du mouvement est obligatoire");
        }
        if (payload.getQuantite() <= 0) {
            throw new IllegalArgumentException("La quantité doit être strictement positive");
        }
        validerCombinaison(payload.getType(), payload.getMotif());
        ProduitStock produit = produitRepository.findById(payload.getProduitId())
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + payload.getProduitId()));

        // Saisie directe : pas de site (stock consolidé « tous sites », bucket siteId=null).
        // Vérification de faisabilité AVANT toute écriture (SORTIE uniquement).
        if (payload.getType() == TypeMouvement.SORTIE) {
            balanceService.verifierDisponibiliteAvecConsolide(produit.getId(), null, payload.getQuantite(), null);
        }

        // Application de l'impact sur le bucket consolidé (siteId=null) : un seul bucket touché,
        // pas de compensation multi-document nécessaire.
        switch (payload.getType()) {
            case ENTREE -> balanceService.appliquerDelta(produit.getId(), null, payload.getQuantite());
            case SORTIE -> balanceService.debiterAvecRepli(produit.getId(), null, payload.getQuantite());
        }

        MouvementStock mvt = MouvementStock.builder()
                .reference(compteurService.genererReference("MVT"))
                .produitId(produit.getId())
                .produitCode(produit.getCode())
                .produitLibelle(produit.getLibelle())
                .unite(produit.getUnite())
                .type(payload.getType())
                .motif(payload.getMotif())
                .quantite(payload.getQuantite())
                .date(payload.getDate() != null ? payload.getDate() : LocalDate.now())
                .utilisateur(currentUser.currentUserNom())
                .commentaire(payload.getCommentaire())
                .origine("DIRECT")
                // 7.6 : snapshot au coût courant ; une entrée directe (sans prix d'achat) ne recalcule pas le coût.
                .coutUnitaireSnapshot(produit.getPrixUnitaire())
                .valeurMouvement(Math.round(payload.getQuantite() * produit.getPrixUnitaire()))
                .createdAt(LocalDateTime.now())
                .build();
        return mapper.toDto(repository.save(mvt));
    }

    public MouvementStock loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mouvement introuvable : " + id));
    }

    /**
     * Combinaisons type/motif autorisées pour une saisie directe :
     * ENTREE → ACHAT | PRODUCTION | RETOUR | AJUSTEMENT ; SORTIE → CONSOMMATION | VENTE | PERTE | AJUSTEMENT.
     */
    private void validerCombinaison(TypeMouvement type, MotifMouvement motif) {
        boolean valide = switch (type) {
            case ENTREE -> motif == MotifMouvement.ACHAT || motif == MotifMouvement.PRODUCTION
                    || motif == MotifMouvement.RETOUR || motif == MotifMouvement.AJUSTEMENT;
            case SORTIE -> motif == MotifMouvement.CONSOMMATION || motif == MotifMouvement.VENTE
                    || motif == MotifMouvement.PERTE || motif == MotifMouvement.AJUSTEMENT;
        };
        if (!valide) {
            throw new IllegalArgumentException("Motif " + motif + " invalide pour un mouvement " + type);
        }
    }
}
