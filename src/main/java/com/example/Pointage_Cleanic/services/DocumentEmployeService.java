package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.Dto.DocumentEmployeDto;
import com.example.Pointage_Cleanic.Dto.ModifierDocumentRequest;
import com.example.Pointage_Cleanic.Dto.ValiderDocumentRequest;
import com.example.Pointage_Cleanic.Enum.CategorieDocument;
import com.example.Pointage_Cleanic.Enum.DecisionDocument;
import com.example.Pointage_Cleanic.Enum.StatutDocument;
import com.example.Pointage_Cleanic.Mapper.DocumentEmployeMapper;
import com.example.Pointage_Cleanic.entities.DocumentEmploye;
import com.example.Pointage_Cleanic.entities.DossierEmploye;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.DocumentEmployeRepository;
import com.example.Pointage_Cleanic.repositories.DossierEmployeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentEmployeService {

    private final DocumentEmployeRepository repository;
    private final DocumentEmployeMapper mapper;
    private final DossierEmployeRepository dossierEmployeRepository;

    public Page<DocumentEmployeDto> list(int page, int size, String employeId, String categorieStr) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateUpload").descending());
        CategorieDocument categorie = parseCategorie(categorieStr);

        Page<DocumentEmploye> result;
        boolean hasEmploye = employeId != null && !employeId.isBlank();
        if (hasEmploye && categorie != null) {
            result = repository.findByEmployeIdAndCategorie(employeId, categorie, pageable);
        } else if (hasEmploye) {
            result = repository.findByEmployeId(employeId, pageable);
        } else if (categorie != null) {
            result = repository.findByCategorie(categorie, pageable);
        } else {
            result = repository.findAll(pageable);
        }
        return result.map(this::toDto);
    }

    public List<DocumentEmployeDto> listByEmploye(String employeId) {
        return repository.findByEmployeId(employeId).stream().map(this::toDto).toList();
    }

    public DocumentEmployeDto getById(String id) {
        return toDto(requireById(id));
    }

    public DocumentEmployeDto upload(DocumentEmployeDto dto, MultipartFile fichier) throws IOException {
        if (dto == null || dto.getEmployeId() == null || dto.getEmployeId().isBlank()) {
            throw new IllegalArgumentException("employeId est obligatoire");
        }
        if (dto.getNom() == null || dto.getNom().isBlank()) {
            throw new IllegalArgumentException("nom est obligatoire");
        }
        if (dto.getCategorie() == null) {
            throw new IllegalArgumentException("categorie est obligatoire");
        }
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("fichier est obligatoire");
        }

        DossierEmploye employe = dossierEmployeRepository.findById(dto.getEmployeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier employé introuvable : " + dto.getEmployeId()));

        DocumentEmploye doc = DocumentEmploye.builder()
                .employeId(employe.getId())
                .employeNom(employe.getNom())
                .employePrenom(employe.getPrenom())
                .nom(dto.getNom().trim())
                .categorie(dto.getCategorie())
                .dateExpiration(dto.getDateExpiration())
                .commentaire(dto.getCommentaire())
                .fichierNom(fichier.getOriginalFilename())
                .typeMime(fichier.getContentType() != null ? fichier.getContentType() : "application/octet-stream")
                .tailleFichier(fichier.getSize())
                .fichier(fichier.getBytes())
                .dateUpload(LocalDateTime.now())
                .statut(StatutDocument.EN_ATTENTE)
                .build();

        return toDto(repository.save(doc));
    }

    public DocumentEmployeDto modifier(String id, ModifierDocumentRequest request) {
        DocumentEmploye doc = requireById(id);
        if (request == null) {
            throw new IllegalArgumentException("Le corps de la requête est obligatoire");
        }
        if (request.nom() != null) {
            if (request.nom().isBlank()) {
                throw new IllegalArgumentException("nom ne peut pas être vide");
            }
            doc.setNom(request.nom().trim());
        }
        if (request.categorie() != null) {
            doc.setCategorie(request.categorie());
        }
        if (request.dateExpiration() != null) {
            doc.setDateExpiration(request.dateExpiration());
        }
        if (request.commentaire() != null) {
            doc.setCommentaire(request.commentaire());
        }
        return toDto(repository.save(doc));
    }

    public DocumentEmployeDto valider(String id, ValiderDocumentRequest request) {
        DocumentEmploye doc = requireById(id);
        if (request == null || request.statut() == null) {
            throw new IllegalArgumentException("statut est obligatoire (VALIDE ou REFUSE)");
        }
        StatutDocument cible = request.statut() == DecisionDocument.VALIDE
                ? StatutDocument.VALIDE
                : StatutDocument.REFUSE;
        doc.setStatut(cible);
        if (request.commentaire() != null) {
            doc.setCommentaire(request.commentaire());
        }
        return toDto(repository.save(doc));
    }

    public void delete(String id) {
        DocumentEmploye doc = requireById(id);
        repository.delete(doc);
    }

    public DocumentEmploye getFichier(String id) {
        DocumentEmploye doc = requireById(id);
        if (doc.getFichier() == null || doc.getFichier().length == 0) {
            throw new ResourceNotFoundException("Aucun fichier attaché au document : " + id);
        }
        return doc;
    }

    private DocumentEmploye requireById(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document introuvable : " + id));
    }

    private DocumentEmployeDto toDto(DocumentEmploye entity) {
        DocumentEmployeDto dto = mapper.toDto(entity);
        dto.setFichierUrl("/api/gestion-personnel/documents/" + entity.getId() + "/telecharger");
        dto.setStatut(deriverStatutAffiche(entity));
        return dto;
    }

    static StatutDocument deriverStatutAffiche(DocumentEmploye entity) {
        StatutDocument courant = entity.getStatut();
        if (entity.getDateExpiration() != null
                && entity.getDateExpiration().isBefore(LocalDate.now())
                && (courant == StatutDocument.VALIDE || courant == StatutDocument.EN_ATTENTE)) {
            return StatutDocument.EXPIRE;
        }
        return courant;
    }

    private CategorieDocument parseCategorie(String categorieStr) {
        if (categorieStr == null || categorieStr.isBlank()) {
            return null;
        }
        try {
            return CategorieDocument.valueOf(categorieStr);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Catégorie de document invalide : " + categorieStr);
        }
    }
}
