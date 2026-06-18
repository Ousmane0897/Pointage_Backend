package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.CategoriePayload;
import com.example.Pointage_Cleanic.Dto.stockv2.CategorieStockDto;
import com.example.Pointage_Cleanic.Mapper.stockv2.CategorieStockMapper;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.exception.EntiteReferenceeException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategorieStockService {

    private final CategorieStockRepository repository;
    private final ProduitStockRepository produitRepository;
    private final CategorieStockMapper mapper;

    public List<CategorieStockDto> racines() {
        return repository.findByParentIdIsNull().stream().map(this::toDtoEnrichi).toList();
    }

    public List<CategorieStockDto> enfants(String parentId) {
        return repository.findByParentId(parentId).stream().map(this::toDtoEnrichi).toList();
    }

    public List<CategorieStockDto> liste() {
        return repository.findAll(Sort.by("libelle").ascending()).stream().map(this::toDtoEnrichi).toList();
    }

    public CategorieStockDto getById(String id) {
        return toDtoEnrichi(loadOrThrow(id));
    }

    public CategorieStockDto create(CategoriePayload payload) {
        if (payload.getLibelle() == null || payload.getLibelle().isBlank()) {
            throw new IllegalArgumentException("Le libellé de la catégorie est obligatoire");
        }
        CategorieStock entity = CategorieStock.builder()
                .libelle(payload.getLibelle().trim())
                .parentId(normalise(payload.getParentId()))
                .niveau(calculerNiveau(normalise(payload.getParentId())))
                .description(payload.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return toDtoEnrichi(repository.save(entity));
    }

    public CategorieStockDto update(String id, CategoriePayload payload) {
        CategorieStock entity = loadOrThrow(id);
        if (payload.getLibelle() != null && !payload.getLibelle().isBlank()) {
            entity.setLibelle(payload.getLibelle().trim());
        }
        String parentId = normalise(payload.getParentId());
        if (parentId != null && parentId.equals(id)) {
            throw new IllegalArgumentException("Une catégorie ne peut pas être son propre parent");
        }
        entity.setParentId(parentId);
        entity.setNiveau(calculerNiveau(parentId));
        entity.setDescription(payload.getDescription());
        entity.setUpdatedAt(LocalDateTime.now());
        return toDtoEnrichi(repository.save(entity));
    }

    public void delete(String id) {
        CategorieStock entity = loadOrThrow(id);
        long enfants = repository.countByParentId(id);
        long produits = produitRepository.countByCategorieId(id);
        if (enfants > 0 || produits > 0) {
            throw new EntiteReferenceeException(
                    "Catégorie non vide (" + enfants + " sous-catégorie(s), " + produits + " produit(s)) — suppression interdite");
        }
        repository.delete(entity);
    }

    /** Résout ou crée une catégorie racine par libellé (utilisé par l'import bulk). */
    public CategorieStock resoudreOuCreerParLibelle(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            return null;
        }
        String cible = libelle.trim();
        return repository.findAll().stream()
                .filter(c -> cible.equalsIgnoreCase(c.getLibelle()))
                .findFirst()
                .orElseGet(() -> repository.save(CategorieStock.builder()
                        .libelle(cible)
                        .parentId(null)
                        .niveau(0)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build()));
    }

    public CategorieStock loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie introuvable : " + id));
    }

    private int calculerNiveau(String parentId) {
        if (parentId == null) {
            return 0;
        }
        CategorieStock parent = repository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie parente introuvable : " + parentId));
        return parent.getNiveau() + 1;
    }

    private String normalise(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private CategorieStockDto toDtoEnrichi(CategorieStock entity) {
        CategorieStockDto dto = mapper.toDto(entity);
        dto.setNbEnfants((int) repository.countByParentId(entity.getId()));
        dto.setNbProduits((int) produitRepository.countByCategorieId(entity.getId()));
        return dto;
    }
}
