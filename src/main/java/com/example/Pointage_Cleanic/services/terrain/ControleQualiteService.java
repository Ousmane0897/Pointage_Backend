package com.example.Pointage_Cleanic.services.terrain;

import com.example.Pointage_Cleanic.Dto.terrain.ControleQualiteTerrainDto;
import com.example.Pointage_Cleanic.Dto.terrain.EvolutionNotePoint;
import com.example.Pointage_Cleanic.Dto.terrain.GrilleEvaluationTerrainDto;
import com.example.Pointage_Cleanic.Enum.terrain.DecisionControleTerrain;
import com.example.Pointage_Cleanic.Mapper.terrain.ControleQualiteTerrainMapper;
import com.example.Pointage_Cleanic.Mapper.terrain.GrilleEvaluationTerrainMapper;
import com.example.Pointage_Cleanic.entities.terrain.ControleQualiteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.GrilleEvaluationTerrain;
import com.example.Pointage_Cleanic.entities.terrain.NotationCritere;
import com.example.Pointage_Cleanic.entities.terrain.PhotoControleFichier;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.terrain.ControleQualiteTerrainRepository;
import com.example.Pointage_Cleanic.repositories.terrain.GrilleEvaluationTerrainRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.example.Pointage_Cleanic.services.terrain.TerrainConstantes.NOTE_SEUIL_CONFORMITE_DEFAUT;

@Service
@RequiredArgsConstructor
public class ControleQualiteService {

    private final GrilleEvaluationTerrainRepository grilleRepository;
    private final ControleQualiteTerrainRepository controleRepository;
    private final GrilleEvaluationTerrainMapper grilleMapper;
    private final ControleQualiteTerrainMapper controleMapper;
    private final MongoTemplate mongoTemplate;
    private final SiteClientService siteService;
    private final CurrentUserProvider currentUser;

    // ───────────────────────── Grilles ─────────────────────────

    public List<GrilleEvaluationTerrainDto> listGrilles() {
        return grilleRepository.findAll().stream().map(grilleMapper::toDto).toList();
    }

    public GrilleEvaluationTerrainDto getGrille(String id) {
        return grilleMapper.toDto(loadGrilleOrThrow(id));
    }

    /** Grille du site, sinon grille générique (siteId null), sinon null. */
    public GrilleEvaluationTerrainDto pourSite(String siteId) {
        GrilleEvaluationTerrain grille = resoudreGrillePourSite(siteId);
        return grille == null ? null : grilleMapper.toDto(grille);
    }

    public GrilleEvaluationTerrainDto createGrille(GrilleEvaluationTerrainDto dto) {
        GrilleEvaluationTerrain entity = grilleMapper.toEntity(dto);
        entity.setId(null);
        if (entity.getNoteSeuilConformite() == null) {
            entity.setNoteSeuilConformite(NOTE_SEUIL_CONFORMITE_DEFAUT);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return grilleMapper.toDto(grilleRepository.save(entity));
    }

    public GrilleEvaluationTerrainDto updateGrille(String id, GrilleEvaluationTerrainDto dto) {
        GrilleEvaluationTerrain entity = loadGrilleOrThrow(id);
        grilleMapper.updateEntityFromDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        return grilleMapper.toDto(grilleRepository.save(entity));
    }

    public void deleteGrille(String id) {
        grilleRepository.delete(loadGrilleOrThrow(id));
    }

    private GrilleEvaluationTerrain loadGrilleOrThrow(String id) {
        return grilleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grille introuvable : " + id));
    }

    private GrilleEvaluationTerrain resoudreGrillePourSite(String siteId) {
        if (siteId != null) {
            List<GrilleEvaluationTerrain> duSite = grilleRepository.findBySiteIdAndActifTrue(siteId);
            if (!duSite.isEmpty()) return duSite.get(0);
        }
        List<GrilleEvaluationTerrain> generiques = grilleRepository.findBySiteIdIsNullAndActifTrue();
        return generiques.isEmpty() ? null : generiques.get(0);
    }

    // ───────────────────────── Contrôles ─────────────────────────

    public PageResponse<ControleQualiteTerrainDto> list(int page, int size, LocalDate dateDebut, LocalDate dateFin,
                                                        DecisionControleTerrain decision, String controleurEmployeId,
                                                        String siteId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("dateControle").descending());
        Query query = new Query().with(pageable);
        if (dateDebut != null) query.addCriteria(Criteria.where("dateControle").gte(dateDebut.atStartOfDay()));
        if (dateFin != null) query.addCriteria(Criteria.where("dateControle").lte(dateFin.atTime(LocalTime.MAX)));
        if (decision != null) query.addCriteria(Criteria.where("decision").is(decision));
        if (controleurEmployeId != null && !controleurEmployeId.isBlank())
            query.addCriteria(Criteria.where("controleurEmployeId").is(controleurEmployeId));
        if (siteId != null && !siteId.isBlank()) query.addCriteria(Criteria.where("siteId").is(siteId));

        List<ControleQualiteTerrain> results = mongoTemplate.find(query, ControleQualiteTerrain.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), ControleQualiteTerrain.class);
        return new PageResponse<>(results.stream().map(controleMapper::toDto).toList(), total);
    }

    public ControleQualiteTerrainDto getControle(String id) {
        return controleMapper.toDto(loadControleOrThrow(id));
    }

    public ControleQualiteTerrainDto create(ControleQualiteTerrainDto dto, MultipartFile[] photos) throws IOException {
        ControleQualiteTerrain entity = controleMapper.toEntity(dto);
        entity.setId(null);

        SiteClient site = siteService.loadOrThrow(entity.getSiteId());
        entity.setSiteCode(site.getCode());
        entity.setSiteNom(site.getNom());

        // Résolution de la grille (explicite ou par site) → seuil de conformité
        GrilleEvaluationTerrain grille = entity.getGrilleId() != null
                ? grilleRepository.findById(entity.getGrilleId()).orElse(null)
                : resoudreGrillePourSite(entity.getSiteId());
        double seuil = NOTE_SEUIL_CONFORMITE_DEFAUT;
        if (grille != null) {
            entity.setGrilleId(grille.getId());
            entity.setGrilleNom(grille.getNom());
            if (grille.getNoteSeuilConformite() != null) {
                seuil = grille.getNoteSeuilConformite();
            }
        }

        // Contrôleur courant si non fourni
        if (entity.getControleurEmployeId() == null || entity.getControleurEmployeId().isBlank()) {
            entity.setControleurEmployeId(currentUser.currentUserId());
            entity.setControleurNom(currentUser.currentUserNom());
        }

        // Note globale recalculée (moyenne pondérée normalisée)
        double note = calculerNoteGlobale(entity.getNotations());
        entity.setNoteGlobale(note);

        // Décision : RESERVES si fournie, sinon CONFORME/NON_CONFORME selon le seuil
        DecisionControleTerrain decision = (dto.getDecision() == DecisionControleTerrain.RESERVES)
                ? DecisionControleTerrain.RESERVES
                : (note >= seuil ? DecisionControleTerrain.CONFORME : DecisionControleTerrain.NON_CONFORME);
        entity.setDecision(decision);

        boolean commentaireObligatoire = decision == DecisionControleTerrain.NON_CONFORME
                || decision == DecisionControleTerrain.RESERVES;
        if (commentaireObligatoire && (entity.getCommentaire() == null || entity.getCommentaire().isBlank())) {
            throw new IllegalArgumentException("Un commentaire est obligatoire pour une décision " + decision);
        }

        if (entity.getDateControle() == null) {
            entity.setDateControle(LocalDateTime.now());
        }
        entity.setPhotos(construirePhotos(photos));
        entity.setCreatedAt(LocalDateTime.now());
        return controleMapper.toDto(controleRepository.save(entity));
    }

    public PhotoControleFichier getPhoto(String id, int index) {
        ControleQualiteTerrain c = loadControleOrThrow(id);
        if (c.getPhotos() == null || index < 0 || index >= c.getPhotos().size()) {
            throw new ResourceNotFoundException("Photo introuvable : contrôle " + id + " index " + index);
        }
        return c.getPhotos().get(index);
    }

    public List<EvolutionNotePoint> historique(String siteId, int nbPoints) {
        List<ControleQualiteTerrain> controles = controleRepository.findBySiteIdOrderByDateControleDesc(siteId);
        List<ControleQualiteTerrain> derniers = controles.stream().limit(Math.max(nbPoints, 0)).toList();
        List<EvolutionNotePoint> points = new ArrayList<>(derniers.stream()
                .map(c -> EvolutionNotePoint.builder()
                        .dateControle(c.getDateControle())
                        .noteGlobale(c.getNoteGlobale())
                        .decision(c.getDecision())
                        .build())
                .toList());
        Collections.reverse(points); // ordre chronologique croissant
        return points;
    }

    private ControleQualiteTerrain loadControleOrThrow(String id) {
        return controleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrôle introuvable : " + id));
    }

    private double calculerNoteGlobale(List<NotationCritere> notations) {
        if (notations == null || notations.isEmpty()) return 0d;
        double sommeNotes = 0d;
        double sommePoids = 0d;
        for (NotationCritere n : notations) {
            double poids = n.getPoids() == null ? 1d : n.getPoids();
            double note = n.getNote() == null ? 0d : n.getNote();
            sommeNotes += note * poids;
            sommePoids += poids;
        }
        return sommePoids == 0d ? 0d : sommeNotes / sommePoids;
    }

    private List<PhotoControleFichier> construirePhotos(MultipartFile[] photos) throws IOException {
        List<PhotoControleFichier> result = new ArrayList<>();
        if (photos == null) return result;
        for (MultipartFile f : photos) {
            if (f == null || f.isEmpty()) continue;
            result.add(PhotoControleFichier.builder()
                    .nomFichier(f.getOriginalFilename())
                    .mimeType(f.getContentType())
                    .contenu(f.getBytes())
                    .build());
        }
        return result;
    }
}