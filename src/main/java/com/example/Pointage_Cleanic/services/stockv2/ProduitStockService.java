package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ProduitBulkRequest;
import com.example.Pointage_Cleanic.Dto.stockv2.ProduitBulkResponse;
import com.example.Pointage_Cleanic.Dto.stockv2.ProduitDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Mapper.stockv2.ProduitStockMapper;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.StockConflitException;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.MouvementStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.StockParSiteRepository;
import com.example.Pointage_Cleanic.services.terrain.CurrentUserProvider;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProduitStockService {

    private final ProduitStockRepository repository;
    private final ProduitStockMapper mapper;
    private final CategorieStockRepository categorieRepository;
    private final StockParSiteRepository stockParSiteRepository;
    private final StockBalanceService balanceService;
    private final MouvementStockRepository mouvementRepository;
    private final CompteurStockService compteurService;
    private final CurrentUserProvider currentUser;
    private final MongoTemplate mongoTemplate;

    // ---------------------------------------------------------------- LISTE

    public PageResponse<ProduitDto> list(int page, int size, String q, TypeProduit typeProduit,
                                         String categorieId, String fournisseur,
                                         Boolean sousSeuil, Boolean actif) {
        Query query = new Query().with(Sort.by("libelle").ascending());
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("code").regex(regex, "i"),
                    Criteria.where("libelle").regex(regex, "i"),
                    Criteria.where("fournisseurPrincipal").regex(regex, "i")
            ));
        }
        if (typeProduit != null) {
            query.addCriteria(Criteria.where("typeProduit").is(typeProduit));
        }
        if (categorieId != null && !categorieId.isBlank()) {
            query.addCriteria(Criteria.where("categorieId").is(categorieId));
        }
        if (fournisseur != null && !fournisseur.isBlank()) {
            query.addCriteria(Criteria.where("fournisseurPrincipal").regex(".*" + Pattern.quote(fournisseur) + ".*", "i"));
        }
        if (actif != null) {
            query.addCriteria(Criteria.where("actif").is(actif));
        }

        List<ProduitStock> matched = mongoTemplate.find(query, ProduitStock.class);

        Map<String, Double> totals = totalsParProduit(matched.stream().map(ProduitStock::getId).toList());
        Map<String, String> categories = libellesCategories(matched);

        List<ProduitDto> dtos = new ArrayList<>(matched.size());
        for (ProduitStock p : matched) {
            double total = totals.getOrDefault(p.getId(), 0.0);
            if (Boolean.TRUE.equals(sousSeuil) && total > p.getSeuilAlerte()) {
                continue;
            }
            dtos.add(toDto(p, total, categories));
        }

        long totalElements = dtos.size();
        int from = Math.min(Math.max(page, 0) * Math.max(size, 1), dtos.size());
        int to = Math.min(from + size, dtos.size());
        List<ProduitDto> pageContent = from >= to ? List.of() : new ArrayList<>(dtos.subList(from, to));
        return new PageResponse<>(pageContent, totalElements);
    }

    public List<ProduitDto> listActifs() {
        List<ProduitStock> actifs = repository.findByActifTrue();
        Map<String, String> categories = libellesCategories(actifs);
        return actifs.stream()
                .sorted((a, b) -> safe(a.getLibelle()).compareToIgnoreCase(safe(b.getLibelle())))
                .map(p -> {
                    ProduitDto dto = mapper.toDto(p);
                    dto.setCategorieLibelle(categories.get(p.getCategorieId()));
                    return dto;
                })
                .toList();
    }

    public ProduitDto getById(String id) {
        ProduitStock p = loadOrThrow(id);
        return toDto(p, balanceService.quantiteTotale(id), libellesCategories(List.of(p)));
    }

    // ---------------------------------------------------------------- CRUD

    public ProduitDto create(ProduitDto dto, MultipartFile photo, MultipartFile ficheTechnique) throws IOException {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new IllegalArgumentException("Le code produit est obligatoire");
        }
        if (repository.existsByCode(dto.getCode())) {
            throw new StockConflitException("Code produit déjà utilisé : " + dto.getCode());
        }
        ProduitStock entity = mapper.toEntity(dto);
        entity.setId(null);
        appliquerPhoto(entity, photo, false);
        appliquerFiche(entity, ficheTechnique, false);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        ProduitStock saved = repository.save(entity);
        return toDto(saved, 0.0, libellesCategories(List.of(saved)));
    }

    public ProduitDto update(String id, ProduitDto dto, MultipartFile photo, MultipartFile ficheTechnique,
                             boolean supprimerPhoto, boolean supprimerFicheTechnique) throws IOException {
        ProduitStock entity = loadOrThrow(id);
        if (dto.getCode() != null && !dto.getCode().equals(entity.getCode())) {
            if (repository.existsByCode(dto.getCode())) {
                throw new StockConflitException("Code produit déjà utilisé : " + dto.getCode());
            }
            entity.setCode(dto.getCode());
        }
        mapper.updateEntityFromDto(dto, entity);
        appliquerPhoto(entity, photo, supprimerPhoto);
        appliquerFiche(entity, ficheTechnique, supprimerFicheTechnique);
        entity.setUpdatedAt(LocalDateTime.now());
        ProduitStock saved = repository.save(entity);
        return toDto(saved, balanceService.quantiteTotale(id), libellesCategories(List.of(saved)));
    }

    public void delete(String id) {
        ProduitStock entity = loadOrThrow(id);
        repository.delete(entity);
    }

    // ---------------------------------------------------------------- 7.6 Valorisation (PATCH dédiés)

    /** Écrit l'override de méthode de valorisation du produit (hors formulaire 7.3). */
    public ProduitDto definirMethodeValorisation(String id,
            com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation methode) {
        ProduitStock entity = loadOrThrow(id);
        entity.setMethodeValorisation(methode);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        return getById(id);
    }

    /** Écrit le prix de vente unitaire du produit (FCFA, hors formulaire 7.3). */
    public ProduitDto definirPrixVente(String id, Long prixVente) {
        if (prixVente != null && prixVente < 0) {
            throw new IllegalArgumentException("Le prix de vente ne peut pas être négatif");
        }
        ProduitStock entity = loadOrThrow(id);
        entity.setPrixVente(prixVente);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        return getById(id);
    }

    public ProduitStock getPhoto(String id) {
        ProduitStock p = loadOrThrow(id);
        if (p.getPhoto() == null) {
            throw new ResourceNotFoundException("Aucune photo pour le produit : " + id);
        }
        return p;
    }

    public ProduitStock getFicheTechnique(String id) {
        ProduitStock p = loadOrThrow(id);
        if (p.getFicheTechnique() == null) {
            throw new ResourceNotFoundException("Aucune fiche technique pour le produit : " + id);
        }
        return p;
    }

    public ProduitStock loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable : " + id));
    }

    // ---------------------------------------------------------------- BULK (transactionnel)

    public ProduitBulkResponse importBulk(ProduitBulkRequest request) {
        List<ProduitBulkRequest.LigneImport> lignes =
                request == null || request.getProduits() == null ? List.of() : request.getProduits();
        int total = lignes.size();

        // 1. Pré-validation complète (aucune écriture)
        List<ProduitBulkResponse.BulkError> errors = new ArrayList<>();
        Set<String> codesVus = new HashSet<>();
        for (ProduitBulkRequest.LigneImport l : lignes) {
            valider(l, codesVus, errors);
        }
        if (!errors.isEmpty()) {
            return echec(total, errors);
        }

        // 2. Insertion avec compensation manuelle (MongoDB standalone, pas de transaction multi-doc)
        List<String> createdProduitIds = new ArrayList<>();
        List<String> createdCategorieIds = new ArrayList<>();
        List<String> createdMouvementIds = new ArrayList<>();
        List<String> createdStockIds = new ArrayList<>();
        Map<String, String> categorieCache = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        String utilisateur = currentUser.currentUserNom();

        try {
            for (ProduitBulkRequest.LigneImport l : lignes) {
                String categorieId = resoudreCategorie(l.getCategorieLibelle(), categorieCache, createdCategorieIds);

                ProduitStock p = ProduitStock.builder()
                        .code(l.getCode().trim())
                        .libelle(l.getLibelle().trim())
                        .typeProduit(l.getTypeProduit())
                        .categorieId(categorieId)
                        .sousCategorie(l.getSousCategorie())
                        .unite(l.getUnite())
                        .fournisseurPrincipal(l.getFournisseurPrincipal())
                        .seuilAlerte(l.getSeuilAlerte())
                        .prixUnitaire(l.getPrixUnitaire())
                        .actif(l.getActif() == null || l.getActif())
                        .remarque(l.getRemarque())
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                p = repository.save(p);
                createdProduitIds.add(p.getId());

                if (l.getStockInitial() != null && l.getStockInitial() != 0.0) {
                    MouvementStock mvt = MouvementStock.builder()
                            .reference(compteurService.genererReference("MVT"))
                            .produitId(p.getId())
                            .produitCode(p.getCode())
                            .produitLibelle(p.getLibelle())
                            .unite(p.getUnite())
                            .type(TypeMouvement.ENTREE)
                            .motif(MotifMouvement.AJUSTEMENT)
                            .quantite(l.getStockInitial())
                            .date(LocalDate.now())
                            .utilisateur(utilisateur)
                            .commentaire("Stock initial (import)")
                            .createdAt(now)
                            .build();
                    mvt = mouvementRepository.save(mvt);
                    createdMouvementIds.add(mvt.getId());

                    StockParSite solde = balanceService.appliquerDelta(p.getId(), null, l.getStockInitial());
                    createdStockIds.add(solde.getId());
                }
            }
        } catch (Exception ex) {
            // ROLLBACK total
            if (!createdStockIds.isEmpty()) {
                stockParSiteRepository.deleteAllById(createdStockIds);
            }
            if (!createdMouvementIds.isEmpty()) {
                mouvementRepository.deleteAllById(createdMouvementIds);
            }
            if (!createdProduitIds.isEmpty()) {
                repository.deleteAllById(createdProduitIds);
            }
            if (!createdCategorieIds.isEmpty()) {
                categorieRepository.deleteAllById(createdCategorieIds);
            }
            return echec(total, List.of(ProduitBulkResponse.BulkError.builder()
                    .message("Échec de l'import, aucune ligne enregistrée : " + ex.getMessage())
                    .build()));
        }

        return ProduitBulkResponse.builder()
                .total(total)
                .inserted(createdProduitIds.size())
                .failed(0)
                .insertedIds(createdProduitIds)
                .errors(List.of())
                .build();
    }

    private void valider(ProduitBulkRequest.LigneImport l, Set<String> codesVus,
                         List<ProduitBulkResponse.BulkError> errors) {
        Integer numero = l.getNumeroLigne();
        if (l.getCode() == null || l.getCode().isBlank()) {
            errors.add(erreur(numero, null, "code", "Code obligatoire"));
            return;
        }
        String code = l.getCode().trim();
        if (!codesVus.add(code.toLowerCase())) {
            errors.add(erreur(numero, code, "code", "Code dupliqué dans le fichier : " + code));
        }
        if (repository.existsByCode(code)) {
            errors.add(erreur(numero, code, "code", "Code déjà utilisé : " + code));
        }
        if (l.getLibelle() == null || l.getLibelle().isBlank()) {
            errors.add(erreur(numero, code, "libelle", "Libellé obligatoire"));
        }
        if (l.getTypeProduit() == null) {
            errors.add(erreur(numero, code, "typeProduit", "Type de produit obligatoire"));
        }
        if (l.getUnite() == null) {
            errors.add(erreur(numero, code, "unite", "Unité obligatoire"));
        }
        if (l.getSeuilAlerte() == null) {
            errors.add(erreur(numero, code, "seuilAlerte", "Seuil d'alerte obligatoire"));
        }
        if (l.getPrixUnitaire() == null) {
            errors.add(erreur(numero, code, "prixUnitaire", "Prix unitaire obligatoire"));
        }
    }

    private String resoudreCategorie(String libelle, Map<String, String> cache, List<String> createdIds) {
        if (libelle == null || libelle.isBlank()) {
            return null;
        }
        String cible = libelle.trim();
        String cacheKey = cible.toLowerCase();
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        CategorieStock existante = categorieRepository.findAll().stream()
                .filter(c -> cible.equalsIgnoreCase(c.getLibelle()))
                .findFirst()
                .orElse(null);
        if (existante != null) {
            cache.put(cacheKey, existante.getId());
            return existante.getId();
        }
        LocalDateTime now = LocalDateTime.now();
        CategorieStock creee = categorieRepository.save(CategorieStock.builder()
                .libelle(cible)
                .parentId(null)
                .niveau(0)
                .createdAt(now)
                .updatedAt(now)
                .build());
        cache.put(cacheKey, creee.getId());
        createdIds.add(creee.getId());
        return creee.getId();
    }

    private ProduitBulkResponse echec(int total, List<ProduitBulkResponse.BulkError> errors) {
        return ProduitBulkResponse.builder()
                .total(total)
                .inserted(0)
                .failed(total)
                .insertedIds(List.of())
                .errors(errors)
                .build();
    }

    private ProduitBulkResponse.BulkError erreur(Integer numero, String code, String field, String message) {
        return ProduitBulkResponse.BulkError.builder()
                .numeroLigne(numero)
                .lineNumber(numero)
                .code(code)
                .field(field)
                .message(message)
                .build();
    }

    // ---------------------------------------------------------------- helpers

    private void appliquerPhoto(ProduitStock entity, MultipartFile photo, boolean supprimer) throws IOException {
        if (supprimer) {
            entity.setPhoto(null);
            entity.setPhotoNom(null);
            entity.setPhotoMimeType(null);
        } else if (photo != null && !photo.isEmpty()) {
            entity.setPhoto(photo.getBytes());
            entity.setPhotoNom(photo.getOriginalFilename());
            entity.setPhotoMimeType(photo.getContentType());
        }
    }

    private void appliquerFiche(ProduitStock entity, MultipartFile fiche, boolean supprimer) throws IOException {
        if (supprimer) {
            entity.setFicheTechnique(null);
            entity.setFicheTechniqueNom(null);
            entity.setFicheTechniqueMimeType(null);
        } else if (fiche != null && !fiche.isEmpty()) {
            entity.setFicheTechnique(fiche.getBytes());
            entity.setFicheTechniqueNom(fiche.getOriginalFilename());
            entity.setFicheTechniqueMimeType(fiche.getContentType());
        }
    }

    private Map<String, Double> totalsParProduit(Collection<String> produitIds) {
        if (produitIds.isEmpty()) {
            return Map.of();
        }
        return stockParSiteRepository.findByProduitIdIn(produitIds).stream()
                .collect(Collectors.groupingBy(StockParSite::getProduitId,
                        Collectors.summingDouble(StockParSite::getQuantite)));
    }

    private Map<String, String> libellesCategories(Collection<ProduitStock> produits) {
        Set<String> ids = produits.stream()
                .map(ProduitStock::getCategorieId)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        categorieRepository.findAllById(ids).forEach(c -> map.put(c.getId(), c.getLibelle()));
        return map;
    }

    private ProduitDto toDto(ProduitStock entity, double quantiteTotale, Map<String, String> categories) {
        ProduitDto dto = mapper.toDto(entity);
        dto.setQuantiteTotale(quantiteTotale);
        dto.setCategorieLibelle(categories.get(entity.getCategorieId()));
        return dto;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
