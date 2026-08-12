package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComptagePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.InventaireDto;
import com.example.Pointage_Cleanic.Dto.stockv2.InventairePlanifPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutInventaire;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Mapper.stockv2.InventaireMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        Inventaire inv = Inventaire.builder()
                .reference(compteurService.genererReference("INV"))
                .libelle(payload.getLibelle())
                .datePlanifiee(payload.getDatePlanifiee())
                .siteId(blankToNull(payload.getSiteId()))
                .siteNom(referentielSite.nomDuSite(blankToNull(payload.getSiteId())))
                .perimetre(payload.getPerimetre())
                .categorieId(blankToNull(payload.getCategorieId()))
                .seuilEcartJustification(payload.getSeuilEcartJustification())
                .statut(StatutInventaire.BROUILLON)
                .lignes(construireLignes(payload))
                .responsable(currentUser.currentUserNom())
                .commentaire(payload.getCommentaire())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return mapper.toDto(repository.save(inv));
    }

    public InventaireDto update(String id, InventairePlanifPayload payload) {
        Inventaire inv = loadOrThrow(id);
        if (inv.getStatut() != StatutInventaire.BROUILLON) {
            throw new StockConflitException("Modification interdite : l'inventaire n'est plus en BROUILLON");
        }
        validerPlanif(payload);
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
        return mapper.toDto(repository.save(inv));
    }

    public void delete(String id) {
        Inventaire inv = loadOrThrow(id);
        if (inv.getStatut() != StatutInventaire.BROUILLON) {
            throw new StockConflitException("Suppression interdite : l'inventaire n'est plus en BROUILLON");
        }
        repository.delete(inv);
    }

    /** BROUILLON -> COMPTAGE : fige qteTheorique au stock système courant de chaque ligne. */
    public InventaireDto demarrerComptage(String id) {
        Inventaire inv = loadOrThrow(id);
        if (inv.getStatut() != StatutInventaire.BROUILLON) {
            throw new StockConflitException("Comptage impossible depuis le statut " + inv.getStatut());
        }
        for (LigneInventaire ligne : inv.getLignes()) {
            ligne.setQteTheorique(stockSysteme(ligne.getProduitId(), inv.getSiteId()));
            if (ligne.getQtePhysique() != null) {
                ligne.setEcart(ligne.getQtePhysique() - ligne.getQteTheorique());
            }
        }
        inv.setStatut(StatutInventaire.COMPTAGE);
        inv.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(inv));
    }

    /** Enregistre qtePhysique + justification, recalcule ecart ; reste en COMPTAGE. */
    public InventaireDto enregistrerComptage(String id, ComptagePayload payload) {
        Inventaire inv = loadOrThrow(id);
        if (inv.getStatut() != StatutInventaire.COMPTAGE) {
            throw new StockConflitException("Saisie de comptage impossible depuis le statut " + inv.getStatut());
        }
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
        inv.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(inv));
    }

    /** COMPTAGE -> VALIDATION : refuse en 422 si un écart > seuil n'est pas justifié. */
    public InventaireDto valider(String id) {
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
        inv.setStatut(StatutInventaire.VALIDATION);
        inv.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(inv));
    }

    /** VALIDATION -> CLOTURE : applique les écarts au stock via des mouvements AJUSTEMENT. */
    public InventaireDto cloturer(String id) {
        Inventaire inv = loadOrThrow(id);
        if (inv.getStatut() != StatutInventaire.VALIDATION) {
            throw new StockConflitException("Clôture impossible depuis le statut " + inv.getStatut());
        }
        String utilisateur = currentUser.currentUserNom();
        LocalDateTime now = LocalDateTime.now();
        List<String> mvtCrees = new ArrayList<>();
        List<LigneInventaire> dejaAppliquees = new ArrayList<>();

        try {
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
                mvt = mouvementRepository.save(mvt);
                mvtCrees.add(mvt.getId());
                balanceService.appliquerDelta(ligne.getProduitId(), siteId, ecart);
                dejaAppliquees.add(ligne);
                if (produit != null) {
                    produit.setUpdatedAt(now);
                    produitRepository.save(produit);
                }
            }
        } catch (RuntimeException ex) {
            // Compensation : annuler les ajustements déjà appliqués
            for (LigneInventaire ligne : dejaAppliquees) {
                double ecart = ligne.getEcart() == null ? 0.0 : ligne.getEcart();
                balanceService.appliquerDelta(ligne.getProduitId(), inv.getSiteId(), -ecart);
            }
            if (!mvtCrees.isEmpty()) {
                mouvementRepository.deleteAllById(mvtCrees);
            }
            throw ex;
        }

        inv.setStatut(StatutInventaire.CLOTURE);
        inv.setDateCloture(LocalDate.now());
        inv.setUpdatedAt(now);
        return mapper.toDto(repository.save(inv));
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
