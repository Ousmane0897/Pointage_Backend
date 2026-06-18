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
    private final ReferentielSiteService referentielSite;
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
        ProduitStock produit = produitRepository.findById(payload.getProduitId())
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + payload.getProduitId()));

        String siteSourceId = blankToNull(payload.getSiteSourceId());
        String siteDestinationId = blankToNull(payload.getSiteDestinationId());
        validerSites(payload.getType(), siteSourceId, siteDestinationId);

        // Vérification de faisabilité AVANT toute écriture
        String nomSource = referentielSite.nomDuSite(siteSourceId);
        String nomDestination = referentielSite.nomDuSite(siteDestinationId);
        if (payload.getType() == TypeMouvement.SORTIE || payload.getType() == TypeMouvement.TRANSFERT) {
            balanceService.verifierDisponibilite(produit.getId(), siteSourceId, payload.getQuantite(), nomSource);
        }

        // Application de l'impact avec compensation manuelle
        boolean sourceDebite = false;
        try {
            switch (payload.getType()) {
                case ENTREE -> balanceService.appliquerDelta(produit.getId(), siteDestinationId, payload.getQuantite());
                case SORTIE -> balanceService.appliquerDelta(produit.getId(), siteSourceId, -payload.getQuantite());
                case TRANSFERT -> {
                    balanceService.appliquerDelta(produit.getId(), siteSourceId, -payload.getQuantite());
                    sourceDebite = true;
                    balanceService.appliquerDelta(produit.getId(), siteDestinationId, payload.getQuantite());
                }
            }
        } catch (RuntimeException ex) {
            if (sourceDebite) {
                balanceService.appliquerDelta(produit.getId(), siteSourceId, payload.getQuantite());
            }
            throw ex;
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
                .siteSourceId(siteSourceId)
                .siteSourceNom(nomSource)
                .siteDestinationId(siteDestinationId)
                .siteDestinationNom(nomDestination)
                .date(payload.getDate() != null ? payload.getDate() : LocalDate.now())
                .utilisateur(currentUser.currentUserNom())
                .commentaire(payload.getCommentaire())
                .origine("DIRECT")
                .createdAt(LocalDateTime.now())
                .build();
        return mapper.toDto(repository.save(mvt));
    }

    public MouvementStock loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mouvement introuvable : " + id));
    }

    private void validerSites(TypeMouvement type, String source, String destination) {
        switch (type) {
            case ENTREE -> {
                if (destination == null) {
                    throw new IllegalArgumentException("ENTREE : le site de destination est obligatoire");
                }
            }
            case SORTIE -> {
                if (source == null) {
                    throw new IllegalArgumentException("SORTIE : le site source est obligatoire");
                }
            }
            case TRANSFERT -> {
                if (source == null || destination == null) {
                    throw new IllegalArgumentException("TRANSFERT : les sites source et destination sont obligatoires");
                }
                if (source.equals(destination)) {
                    throw new IllegalArgumentException("TRANSFERT : les sites source et destination doivent être différents");
                }
            }
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
