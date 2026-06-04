package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ControleQualiteDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.TendanceParametre;
import com.example.Pointage_Cleanic.Enum.DecisionControle;
import com.example.Pointage_Cleanic.Enum.StatutControleLot;
import com.example.Pointage_Cleanic.Enum.StatutStockLot;
import com.example.Pointage_Cleanic.Mapper.productionchimie.ControleQualiteMapper;
import com.example.Pointage_Cleanic.entities.productionchimie.ControleQualite;
import com.example.Pointage_Cleanic.entities.productionchimie.GrilleControle;
import com.example.Pointage_Cleanic.entities.productionchimie.Lot;
import com.example.Pointage_Cleanic.entities.productionchimie.MesureControle;
import com.example.Pointage_Cleanic.entities.productionchimie.PhotoControle;
import com.example.Pointage_Cleanic.entities.productionchimie.TestControle;
import com.example.Pointage_Cleanic.exception.ControleQualiteInvalideException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.productionchimie.ControleQualiteRepository;
import com.example.Pointage_Cleanic.repositories.productionchimie.GrilleControleRepository;
import com.example.Pointage_Cleanic.repositories.productionchimie.LotRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service("productionControleQualiteService")
@RequiredArgsConstructor
public class ControleQualiteService {

    private final ControleQualiteRepository repository;
    private final ControleQualiteMapper mapper;
    private final LotRepository lotRepository;
    private final GrilleControleRepository grilleRepository;
    private final MongoTemplate mongoTemplate;

    public PageResponse<ControleQualiteDto> list(int page, int size, String lotId, String produitNom,
                                                 DecisionControle decision, LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateControle"));
        Query query = new Query().with(pageable);
        List<Criteria> criterias = new ArrayList<>();
        if (lotId != null && !lotId.isBlank()) criterias.add(Criteria.where("lotId").is(lotId));
        if (produitNom != null && !produitNom.isBlank()) criterias.add(Criteria.where("produitNom").is(produitNom));
        if (decision != null) criterias.add(Criteria.where("decision").is(decision));
        if (dateDebut != null) criterias.add(Criteria.where("dateControle").gte(dateDebut.atStartOfDay()));
        if (dateFin != null) criterias.add(Criteria.where("dateControle").lte(dateFin.atTime(23, 59, 59)));
        if (!criterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));
        }
        List<ControleQualite> results = mongoTemplate.find(query, ControleQualite.class);
        Query countQuery = Query.of(query).limit(-1).skip(-1);
        long total = mongoTemplate.count(countQuery, ControleQualite.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public ControleQualiteDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public ControleQualite loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contrôle qualité introuvable : " + id));
    }

    public ControleQualiteDto create(ControleQualiteDto dto, MultipartFile[] photos) throws IOException {
        if (dto.getDecision() == DecisionControle.REJET
                && (dto.getCommentaire() == null || dto.getCommentaire().isBlank())) {
            throw new ControleQualiteInvalideException("Commentaire obligatoire en cas de rejet");
        }

        Lot lot = lotRepository.findById(dto.getLotId())
                .orElseThrow(() -> new ResourceNotFoundException("Lot introuvable : " + dto.getLotId()));

        ControleQualite entity = mapper.toEntity(dto);
        entity.setId(null);
        entity.setLotNumero(lot.getNumero());
        if (entity.getProduitNom() == null) entity.setProduitNom(lot.getProduitNom());
        if (entity.getDateControle() == null) entity.setDateControle(LocalDateTime.now());
        entity.setCreatedAt(LocalDateTime.now());

        List<PhotoControle> photoEntities = new ArrayList<>();
        if (photos != null) {
            for (MultipartFile p : photos) {
                if (p == null || p.isEmpty()) continue;
                photoEntities.add(PhotoControle.builder()
                        .nomFichier(p.getOriginalFilename())
                        .mimeType(p.getContentType())
                        .taille(p.getSize())
                        .contenu(p.getBytes())
                        .build());
            }
        }
        entity.setPhotos(photoEntities);

        ControleQualite saved = repository.save(entity);

        StatutControleLot statutControle = saved.getDecision() == DecisionControle.VALIDE
                ? StatutControleLot.VALIDE
                : StatutControleLot.REJETE;
        StatutStockLot statutStock = saved.getDecision() == DecisionControle.VALIDE
                ? StatutStockLot.EN_STOCK
                : StatutStockLot.BLOQUE;
        lot.setStatutControle(statutControle);
        lot.setStatutStock(statutStock);
        lot.setControleQualiteId(saved.getId());
        lotRepository.save(lot);

        return mapper.toDto(saved);
    }

    public PhotoControle getPhoto(String controleId, int index) {
        ControleQualite ctrl = loadOrThrow(controleId);
        if (ctrl.getPhotos() == null || index < 0 || index >= ctrl.getPhotos().size()) {
            throw new ResourceNotFoundException("Photo introuvable à l'index " + index);
        }
        return ctrl.getPhotos().get(index);
    }

    public List<TendanceParametre> tendances(String produitNom, Integer nbPoints) {
        if (produitNom == null || produitNom.isBlank()) {
            throw new IllegalArgumentException("produitNom obligatoire");
        }
        int n = nbPoints == null ? 20 : nbPoints;
        if (n <= 0) n = 20;

        GrilleControle grille = grilleRepository.findByProduitNom(produitNom).stream()
                .findFirst().orElse(null);
        if (grille == null || grille.getTests() == null || grille.getTests().isEmpty()) {
            return List.of();
        }

        Query controleQuery = new Query(Criteria.where("produitNom").is(produitNom))
                .with(Sort.by(Sort.Direction.DESC, "dateControle"))
                .limit(n);
        List<ControleQualite> controles = mongoTemplate.find(controleQuery, ControleQualite.class);
        controles.sort(Comparator.comparing(ControleQualite::getDateControle));

        List<TendanceParametre> result = new ArrayList<>();
        for (TestControle test : grille.getTests()) {
            if (test.getType() != com.example.Pointage_Cleanic.Enum.TypeTest.NUMERIQUE) continue;
            List<TendanceParametre.Point> points = new ArrayList<>();
            for (ControleQualite ctrl : controles) {
                if (ctrl.getMesures() == null) continue;
                MesureControle mesure = ctrl.getMesures().stream()
                        .filter(m -> Objects.equals(m.getParametre(), test.getParametre()))
                        .findFirst().orElse(null);
                if (mesure == null || mesure.getValeurMesuree() == null) continue;
                Double valeur = parseDouble(mesure.getValeurMesuree());
                if (valeur == null) continue;
                points.add(TendanceParametre.Point.builder()
                        .date(ctrl.getDateControle())
                        .lotNumero(ctrl.getLotNumero())
                        .valeur(valeur)
                        .conforme(mesure.isConforme())
                        .build());
            }
            result.add(TendanceParametre.builder()
                    .parametre(test.getParametre())
                    .unite(test.getUnite())
                    .valeurCible(test.getValeurCible())
                    .points(points)
                    .build());
        }
        return result;
    }

    private Double parseDouble(String s) {
        if (s == null) return null;
        try {
            return Double.parseDouble(s.replace(",", "."));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
