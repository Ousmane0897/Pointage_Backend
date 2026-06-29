package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ConsommationPlafondDto;
import com.example.Pointage_Cleanic.Dto.stockv2.NotificationStockDto;
import com.example.Pointage_Cleanic.Dto.stockv2.PlafondDto;
import com.example.Pointage_Cleanic.Dto.stockv2.PlafondPayload;
import com.example.Pointage_Cleanic.Enum.stockv2.GranularitePlafond;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Mapper.stockv2.PlafondMapper;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.Plafond;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.PlafondRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Plafonds de dotation : CRUD + consommation mensuelle (jauges). */
@Service
@RequiredArgsConstructor
public class PlafondService {

    private final PlafondRepository repository;
    private final PlafondMapper mapper;
    private final ReferentielSiteService referentielSite;
    private final ProduitStockRepository produitRepository;
    private final CategorieStockRepository categorieRepository;
    private final MongoTemplate mongoTemplate;
    private final StockNotificationService notificationService;

    public PageResponse<PlafondDto> list(int page, int size, String q, String siteId,
                                         GranularitePlafond granularite, Boolean actif) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Query query = new Query().with(pageable);
        if (q != null && !q.isBlank()) {
            String regex = ".*" + Pattern.quote(q) + ".*";
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("commentaire").regex(regex, "i"),
                    Criteria.where("cibleId").regex(regex, "i")
            ));
        }
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteId").is(siteId));
        }
        if (granularite != null) {
            query.addCriteria(Criteria.where("granularite").is(granularite));
        }
        if (actif != null) {
            query.addCriteria(Criteria.where("actif").is(actif));
        }
        List<Plafond> results = mongoTemplate.find(query, Plafond.class);
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Plafond.class);
        return new PageResponse<>(results.stream().map(this::enrichir).toList(), total);
    }

    public PlafondDto getById(String id) {
        return enrichir(loadOrThrow(id));
    }

    public PlafondDto creer(PlafondPayload payload) {
        valider(payload);
        LocalDateTime now = LocalDateTime.now();
        Plafond plafond = Plafond.builder()
                .siteId(payload.getSiteId())
                .granularite(payload.getGranularite())
                .cibleId(payload.getCibleId())
                .plafondMensuel(payload.getPlafondMensuel())
                .actif(payload.isActif())
                .commentaire(payload.getCommentaire())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return enrichir(repository.save(plafond));
    }

    public PlafondDto modifier(String id, PlafondPayload payload) {
        Plafond plafond = loadOrThrow(id);
        valider(payload);
        plafond.setSiteId(payload.getSiteId());
        plafond.setGranularite(payload.getGranularite());
        plafond.setCibleId(payload.getCibleId());
        plafond.setPlafondMensuel(payload.getPlafondMensuel());
        plafond.setActif(payload.isActif());
        plafond.setCommentaire(payload.getCommentaire());
        plafond.setUpdatedAt(LocalDateTime.now());
        return enrichir(repository.save(plafond));
    }

    public void supprimer(String id) {
        repository.delete(loadOrThrow(id));
    }

    public List<ConsommationPlafondDto> consommation(String mois, String siteId) {
        YearMonth ym = parseMois(mois);
        LocalDate debut = ym.atDay(1);
        LocalDate fin = ym.atEndOfMonth();

        List<Plafond> plafonds = (siteId != null && !siteId.isBlank())
                ? repository.findBySiteIdAndActifTrue(siteId)
                : repository.findByActifTrue();

        List<MouvementStock> sorties = sortiesDuMois(debut, fin, siteId);

        List<ConsommationPlafondDto> result = new ArrayList<>();
        for (Plafond p : plafonds) {
            double consomme = consommePourPlafond(p, sorties);
            boolean depassement = p.getPlafondMensuel() > 0 && consomme > p.getPlafondMensuel();
            double pourcentage = p.getPlafondMensuel() == 0 ? 0 : (consomme / p.getPlafondMensuel()) * 100;

            ConsommationPlafondDto dto = ConsommationPlafondDto.builder()
                    .plafondId(p.getId())
                    .siteId(p.getSiteId())
                    .siteNom(referentielSite.nomDuSite(p.getSiteId()))
                    .granularite(p.getGranularite())
                    .cibleId(p.getCibleId())
                    .cibleLibelle(cibleLibelle(p))
                    .unite(uniteCible(p))
                    .plafondMensuel(p.getPlafondMensuel())
                    .consomme(consomme)
                    .pourcentage(pourcentage)
                    .depassement(depassement)
                    .mois(ym.toString())
                    .build();
            result.add(dto);

            if (depassement) {
                notifierDepassement(dto);
            }
        }
        return result;
    }

    private double consommePourPlafond(Plafond p, List<MouvementStock> sorties) {
        if (p.getGranularite() == GranularitePlafond.PRODUIT) {
            return sorties.stream()
                    .filter(m -> p.getSiteId().equals(m.getSiteSourceId()))
                    .filter(m -> p.getCibleId().equals(m.getProduitId()))
                    .mapToDouble(MouvementStock::getQuantite).sum();
        }
        // CATEGORIE : somme des sorties dont le produit appartient à la catégorie
        return sorties.stream()
                .filter(m -> p.getSiteId().equals(m.getSiteSourceId()))
                .filter(m -> categorieDuProduit(m.getProduitId()).map(p.getCibleId()::equals).orElse(false))
                .mapToDouble(MouvementStock::getQuantite).sum();
    }

    private java.util.Optional<String> categorieDuProduit(String produitId) {
        return produitRepository.findById(produitId).map(ProduitStock::getCategorieId);
    }

    private List<MouvementStock> sortiesDuMois(LocalDate debut, LocalDate fin, String siteId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("type").is(TypeMouvement.SORTIE));
        query.addCriteria(Criteria.where("date").gte(debut).lte(fin));
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteSourceId").is(siteId));
        }
        return mongoTemplate.find(query, MouvementStock.class);
    }

    private void valider(PlafondPayload payload) {
        if (payload.getGranularite() == null) {
            throw new IllegalArgumentException("La granularité est obligatoire");
        }
        if (payload.getCibleId() == null || payload.getCibleId().isBlank()) {
            throw new IllegalArgumentException("La cible (cibleId) est obligatoire");
        }
        referentielSite.valideEtCharge(payload.getSiteId());
        if (payload.getGranularite() == GranularitePlafond.PRODUIT) {
            if (produitRepository.findById(payload.getCibleId()).isEmpty()) {
                throw new ResourceNotFoundException("Produit introuvable : " + payload.getCibleId());
            }
        } else if (categorieRepository.findById(payload.getCibleId()).isEmpty()) {
            throw new ResourceNotFoundException("Catégorie introuvable : " + payload.getCibleId());
        }
    }

    private PlafondDto enrichir(Plafond plafond) {
        PlafondDto dto = mapper.toDto(plafond);
        dto.setSiteNom(referentielSite.nomDuSite(plafond.getSiteId()));
        dto.setCibleLibelle(cibleLibelle(plafond));
        dto.setUnite(uniteCible(plafond));
        return dto;
    }

    private String cibleLibelle(Plafond p) {
        if (p.getGranularite() == GranularitePlafond.PRODUIT) {
            return produitRepository.findById(p.getCibleId()).map(ProduitStock::getLibelle).orElse(null);
        }
        return categorieRepository.findById(p.getCibleId()).map(CategorieStock::getLibelle).orElse(null);
    }

    private com.example.Pointage_Cleanic.Enum.stockv2.UniteStock uniteCible(Plafond p) {
        if (p.getGranularite() != GranularitePlafond.PRODUIT) {
            return null;
        }
        return produitRepository.findById(p.getCibleId()).map(ProduitStock::getUnite).orElse(null);
    }

    private void notifierDepassement(ConsommationPlafondDto dto) {
        notificationService.diffuser(NotificationStockDto.builder()
                .type("INFO")
                .sens("SORTIE")
                .reference(dto.getPlafondId())
                .titre("Dépassement de plafond")
                .message("Le plafond de « " + dto.getCibleLibelle() + " » sur " + dto.getSiteNom()
                        + " est dépassé (" + Math.round(dto.getPourcentage()) + "%).")
                .dateEmission(LocalDateTime.now().toString())
                .build());
    }

    private Plafond loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plafond introuvable : " + id));
    }

    private YearMonth parseMois(String mois) {
        if (mois == null || mois.isBlank()) {
            throw new IllegalArgumentException("Le paramètre mois (YYYY-MM) est obligatoire");
        }
        try {
            return YearMonth.parse(mois);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Format de mois invalide (attendu YYYY-MM) : " + mois);
        }
    }
}
