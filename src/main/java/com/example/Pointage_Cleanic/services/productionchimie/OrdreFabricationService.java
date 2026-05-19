package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.AnnulerOfPayload;
import com.example.Pointage_Cleanic.Dto.productionchimie.DisponibiliteOf;
import com.example.Pointage_Cleanic.Dto.productionchimie.LancerOfPayload;
import com.example.Pointage_Cleanic.Dto.productionchimie.OrdreFabricationDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.TerminerOfPayload;
import com.example.Pointage_Cleanic.Enum.StatutControleLot;
import com.example.Pointage_Cleanic.Enum.StatutOf;
import com.example.Pointage_Cleanic.Enum.StatutStockLot;
import com.example.Pointage_Cleanic.Enum.TypeMouvementChimie;
import com.example.Pointage_Cleanic.Mapper.productionchimie.OrdreFabricationMapper;
import com.example.Pointage_Cleanic.entities.productionchimie.ConsommationMp;
import com.example.Pointage_Cleanic.entities.productionchimie.FicheFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.HistoriqueStatutOf;
import com.example.Pointage_Cleanic.entities.productionchimie.IngredientFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.Lot;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import com.example.Pointage_Cleanic.entities.productionchimie.MouvementStockChimie;
import com.example.Pointage_Cleanic.entities.productionchimie.OrdreFabrication;
import com.example.Pointage_Cleanic.entities.productionchimie.VersionFormulation;
import com.example.Pointage_Cleanic.exception.ProductionException;
import com.example.Pointage_Cleanic.exception.ResourceNotFoundException;
import com.example.Pointage_Cleanic.exception.StockChimieInsuffisantException;
import com.example.Pointage_Cleanic.exception.TransitionOfInterditeException;
import com.example.Pointage_Cleanic.repositories.productionchimie.LotRepository;
import com.example.Pointage_Cleanic.repositories.productionchimie.MouvementStockChimieRepository;
import com.example.Pointage_Cleanic.repositories.productionchimie.OrdreFabricationRepository;
import com.example.Pointage_Cleanic.util.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrdreFabricationService {

    private final OrdreFabricationRepository repository;
    private final OrdreFabricationMapper mapper;
    private final FormulationService formulationService;
    private final MatierePremiereService matierePremiereService;
    private final MouvementStockChimieRepository mouvementRepository;
    private final LotRepository lotRepository;
    private final CompteurOfService compteurOfService;
    private final CompteurLotService compteurLotService;
    private final MongoTemplate mongoTemplate;

    public PageResponse<OrdreFabricationDto> list(int page, int size, String q, StatutOf statut,
                                                  String operateurId, String formulationId,
                                                  LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Query query = new Query().with(pageable);
        List<Criteria> criterias = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String regex = ".*" + java.util.regex.Pattern.quote(q) + ".*";
            criterias.add(new Criteria().orOperator(
                    Criteria.where("numero").regex(regex, "i"),
                    Criteria.where("produitNom").regex(regex, "i")
            ));
        }
        if (statut != null) criterias.add(Criteria.where("statut").is(statut));
        if (operateurId != null && !operateurId.isBlank()) criterias.add(Criteria.where("operateurResponsableId").is(operateurId));
        if (formulationId != null && !formulationId.isBlank()) criterias.add(Criteria.where("formulationId").is(formulationId));
        if (dateDebut != null) criterias.add(Criteria.where("dateLancementPrevue").gte(dateDebut.atStartOfDay()));
        if (dateFin != null) criterias.add(Criteria.where("dateLancementPrevue").lte(dateFin.atTime(23, 59, 59)));
        if (!criterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));
        }
        List<OrdreFabrication> results = mongoTemplate.find(query, OrdreFabrication.class);
        Query countQuery = Query.of(query).limit(-1).skip(-1);
        long total = mongoTemplate.count(countQuery, OrdreFabrication.class);
        return new PageResponse<>(results.stream().map(mapper::toDto).toList(), total);
    }

    public List<OrdreFabricationDto> kanban() {
        Pageable top = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"));
        Query query = new Query().with(top);
        return mongoTemplate.find(query, OrdreFabrication.class).stream().map(mapper::toDto).toList();
    }

    public OrdreFabricationDto getById(String id) {
        return mapper.toDto(loadOrThrow(id));
    }

    public OrdreFabrication loadOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OF introuvable : " + id));
    }

    public DisponibiliteOf disponibiliteForFormulation(String formulationId, Double quantiteCible) {
        FicheFormulation form = formulationService.loadOrThrow(formulationId);
        return computeDisponibilite(form, form.getVersionCourante(), quantiteCible);
    }

    public DisponibiliteOf disponibiliteForOf(String id) {
        OrdreFabrication of = loadOrThrow(id);
        FicheFormulation form = formulationService.loadOrThrow(of.getFormulationId());
        return computeDisponibilite(form, of.getFormulationVersion(), of.getQuantiteCible());
    }

    private DisponibiliteOf computeDisponibilite(FicheFormulation form, Integer versionUtilisee, Double quantiteCible) {
        List<IngredientFormulation> ingredients = resolveIngredients(form, versionUtilisee);
        List<DisponibiliteOf.IngredientDisponibilite> details = new ArrayList<>();
        boolean glob = true;
        for (IngredientFormulation i : ingredients) {
            MatierePremiere mp = matierePremiereService.loadOrThrow(i.getMatierePremiereId());
            double requis = i.getDosage() * quantiteCible;
            double stock = mp.getQuantiteEnStock() == null ? 0.0 : mp.getQuantiteEnStock();
            boolean ok = stock >= requis;
            if (!ok) glob = false;
            details.add(DisponibiliteOf.IngredientDisponibilite.builder()
                    .matierePremiereId(mp.getId())
                    .matierePremiereNom(mp.getNom())
                    .quantiteRequise(requis)
                    .quantiteEnStock(stock)
                    .suffisant(ok)
                    .manquant(ok ? null : requis - stock)
                    .build());
        }
        return DisponibiliteOf.builder()
                .formulationId(form.getId())
                .quantiteCible(quantiteCible)
                .uniteCible(form.getUniteProduction())
                .globalementDisponible(glob)
                .ingredients(details)
                .build();
    }

    public OrdreFabricationDto create(OrdreFabricationDto dto) {
        FicheFormulation form = formulationService.loadOrThrow(dto.getFormulationId());
        OrdreFabrication of = mapper.toEntity(dto);
        of.setId(null);
        of.setNumero(compteurOfService.genererNumero());
        if (of.getFormulationVersion() == null) {
            of.setFormulationVersion(form.getVersionCourante());
        }
        of.setFormulationCode(form.getCode());
        of.setStatut(StatutOf.EN_ATTENTE);
        of.setHistoriqueStatuts(new ArrayList<>());
        of.setConsommationMp(new ArrayList<>());
        LocalDateTime now = LocalDateTime.now();
        of.setCreatedAt(now);
        of.setUpdatedAt(now);
        addHistorique(of, null, StatutOf.EN_ATTENTE, "Création de l'OF");
        return mapper.toDto(repository.save(of));
    }

    public OrdreFabricationDto update(String id, OrdreFabricationDto dto) {
        OrdreFabrication of = loadOrThrow(id);
        if (of.getStatut() != StatutOf.EN_ATTENTE) {
            throw new TransitionOfInterditeException("Modification interdite quand statut=" + of.getStatut());
        }
        mapper.updateEntityFromDto(dto, of);
        of.setUpdatedAt(LocalDateTime.now());
        return mapper.toDto(repository.save(of));
    }

    public OrdreFabricationDto lancer(String id, LancerOfPayload payload) {
        OrdreFabrication of = loadOrThrow(id);
        if (of.getStatut() != StatutOf.EN_ATTENTE) {
            throw new TransitionOfInterditeException("Lancement interdit depuis " + of.getStatut());
        }

        FicheFormulation form = formulationService.loadOrThrow(of.getFormulationId());
        List<IngredientFormulation> ingredients = resolveIngredients(form, of.getFormulationVersion());

        List<ConsommationMp> consommationsCalc = new ArrayList<>();
        for (IngredientFormulation i : ingredients) {
            consommationsCalc.add(ConsommationMp.builder()
                    .matierePremiereId(i.getMatierePremiereId())
                    .matierePremiereNom(i.getMatierePremiereNom())
                    .unite(i.getUnite())
                    .quantiteTheorique(i.getDosage() * of.getQuantiteCible())
                    .build());
        }

        for (ConsommationMp c : consommationsCalc) {
            MatierePremiere mp = matierePremiereService.loadOrThrow(c.getMatierePremiereId());
            double stock = mp.getQuantiteEnStock() == null ? 0.0 : mp.getQuantiteEnStock();
            if (stock < c.getQuantiteTheorique()) {
                throw new StockChimieInsuffisantException(
                        "MP " + mp.getCode() + " insuffisante : requis " + c.getQuantiteTheorique() + ", dispo " + stock);
            }
        }

        List<ConsommationMp> consommationsFinales = new ArrayList<>(consommationsCalc.size());
        List<MouvementStockChimie> mvtsCreesPourRollback = new ArrayList<>();
        try {
            for (ConsommationMp c : consommationsCalc) {
                MatierePremiere mp = matierePremiereService.loadOrThrow(c.getMatierePremiereId());
                MouvementStockChimie mvt = MouvementStockChimie.builder()
                        .matierePremiereId(mp.getId())
                        .matierePremiereCode(mp.getCode())
                        .matierePremiereNom(mp.getNom())
                        .unite(mp.getUnite())
                        .type(TypeMouvementChimie.SORTIE)
                        .quantite(c.getQuantiteTheorique())
                        .date(LocalDateTime.now())
                        .ordreFabricationId(of.getId())
                        .ordreFabricationNumero(of.getNumero())
                        .commentaire("Sortie automatique lancement OF " + of.getNumero())
                        .build();
                mvt = mouvementRepository.save(mvt);
                mvtsCreesPourRollback.add(mvt);

                mongoTemplate.updateFirst(
                        new Query(Criteria.where("_id").is(mp.getId())),
                        new Update().inc("quantiteEnStock", -c.getQuantiteTheorique()).set("updatedAt", LocalDateTime.now()),
                        MatierePremiere.class
                );

                consommationsFinales.add(ConsommationMp.builder()
                        .matierePremiereId(c.getMatierePremiereId())
                        .matierePremiereNom(c.getMatierePremiereNom())
                        .unite(c.getUnite())
                        .quantiteTheorique(c.getQuantiteTheorique())
                        .mouvementStockId(mvt.getId())
                        .build());
            }
        } catch (Exception ex) {
            log.error("Échec lancement OF {} — compensation des {} sorties déjà créées",
                    of.getNumero(), mvtsCreesPourRollback.size(), ex);
            for (MouvementStockChimie mvtFait : mvtsCreesPourRollback) {
                MouvementStockChimie inverse = MouvementStockChimie.builder()
                        .matierePremiereId(mvtFait.getMatierePremiereId())
                        .matierePremiereCode(mvtFait.getMatierePremiereCode())
                        .matierePremiereNom(mvtFait.getMatierePremiereNom())
                        .unite(mvtFait.getUnite())
                        .type(TypeMouvementChimie.ENTREE)
                        .quantite(mvtFait.getQuantite())
                        .date(LocalDateTime.now())
                        .ordreFabricationId(of.getId())
                        .ordreFabricationNumero(of.getNumero())
                        .commentaire("Compensation échec lancement OF " + of.getNumero())
                        .build();
                mouvementRepository.save(inverse);
                mongoTemplate.updateFirst(
                        new Query(Criteria.where("_id").is(mvtFait.getMatierePremiereId())),
                        new Update().inc("quantiteEnStock", mvtFait.getQuantite()).set("updatedAt", LocalDateTime.now()),
                        MatierePremiere.class
                );
            }
            throw new ProductionException("Échec lancement OF " + of.getNumero() + " : " + ex.getMessage(), ex);
        }

        of.setConsommationMp(consommationsFinales);
        of.setStatut(StatutOf.EN_COURS);
        of.setDateLancementEffective(LocalDateTime.now());
        of.setUpdatedAt(LocalDateTime.now());
        addHistorique(of, StatutOf.EN_ATTENTE, StatutOf.EN_COURS, payload != null ? payload.getCommentaire() : null);
        return mapper.toDto(repository.save(of));
    }

    public OrdreFabricationDto terminer(String id, TerminerOfPayload payload) {
        OrdreFabrication of = loadOrThrow(id);
        if (of.getStatut() != StatutOf.EN_COURS) {
            throw new TransitionOfInterditeException("Terminaison interdite depuis " + of.getStatut());
        }

        String numeroLot = compteurLotService.genererNumero();
        FicheFormulation form = formulationService.loadOrThrow(of.getFormulationId());
        Integer dureePeremption = resolveDureePeremption(form, of.getFormulationVersion());
        LocalDateTime now = LocalDateTime.now();
        LocalDate datePeremption = (dureePeremption == null)
                ? null
                : now.toLocalDate().plusDays(dureePeremption);

        Lot lot = Lot.builder()
                .numero(numeroLot)
                .ordreFabricationId(of.getId())
                .ordreFabricationNumero(of.getNumero())
                .produitNom(of.getProduitNom())
                .formulationId(of.getFormulationId())
                .formulationCode(of.getFormulationCode())
                .formulationVersion(of.getFormulationVersion())
                .dateFabrication(now)
                .datePeremption(datePeremption)
                .quantiteProduite(of.getQuantiteCible())
                .uniteProduite(of.getUniteCible())
                .statutControle(StatutControleLot.EN_ATTENTE_CONTROLE)
                .statutStock(StatutStockLot.EN_PRODUCTION)
                .createdAt(now)
                .build();
        lot = lotRepository.save(lot);

        of.setLotGenereId(lot.getId());
        of.setLotGenereNumero(lot.getNumero());
        of.setStatut(StatutOf.TERMINE);
        of.setDateFin(now);
        of.setUpdatedAt(now);
        addHistorique(of, StatutOf.EN_COURS, StatutOf.TERMINE, payload != null ? payload.getCommentaire() : null);
        return mapper.toDto(repository.save(of));
    }

    public OrdreFabricationDto annuler(String id, AnnulerOfPayload payload) {
        if (payload == null || payload.getMotif() == null || payload.getMotif().isBlank()) {
            throw new IllegalArgumentException("Motif obligatoire pour l'annulation d'un OF");
        }
        OrdreFabrication of = loadOrThrow(id);
        if (of.getStatut() != StatutOf.EN_ATTENTE && of.getStatut() != StatutOf.EN_COURS) {
            throw new TransitionOfInterditeException("Annulation interdite depuis " + of.getStatut());
        }
        StatutOf precedent = of.getStatut();

        if (precedent == StatutOf.EN_COURS && of.getConsommationMp() != null) {
            for (ConsommationMp c : of.getConsommationMp()) {
                if (c.getMouvementStockId() == null || c.getQuantiteTheorique() == null) continue;
                MouvementStockChimie inverse = MouvementStockChimie.builder()
                        .matierePremiereId(c.getMatierePremiereId())
                        .matierePremiereNom(c.getMatierePremiereNom())
                        .unite(c.getUnite())
                        .type(TypeMouvementChimie.ENTREE)
                        .quantite(c.getQuantiteTheorique())
                        .date(LocalDateTime.now())
                        .ordreFabricationId(of.getId())
                        .ordreFabricationNumero(of.getNumero())
                        .commentaire("Rollback annulation OF " + of.getNumero())
                        .build();
                mouvementRepository.save(inverse);
                mongoTemplate.updateFirst(
                        new Query(Criteria.where("_id").is(c.getMatierePremiereId())),
                        new Update().inc("quantiteEnStock", c.getQuantiteTheorique()).set("updatedAt", LocalDateTime.now()),
                        MatierePremiere.class
                );
            }
        }

        of.setStatut(StatutOf.ANNULE);
        of.setUpdatedAt(LocalDateTime.now());
        addHistorique(of, precedent, StatutOf.ANNULE, payload.getMotif());
        return mapper.toDto(repository.save(of));
    }

    private List<IngredientFormulation> resolveIngredients(FicheFormulation form, Integer versionUtilisee) {
        if (versionUtilisee == null || Objects.equals(versionUtilisee, form.getVersionCourante())) {
            return form.getIngredients();
        }
        VersionFormulation snapshot = form.getVersions() == null ? null : form.getVersions().stream()
                .filter(v -> Objects.equals(v.getNumero(), versionUtilisee))
                .findFirst()
                .orElse(null);
        return snapshot != null ? snapshot.getIngredients() : form.getIngredients();
    }

    private Integer resolveDureePeremption(FicheFormulation form, Integer versionUtilisee) {
        if (versionUtilisee == null || Objects.equals(versionUtilisee, form.getVersionCourante())) {
            return form.getDureePeremptionJours();
        }
        return form.getVersions() == null ? form.getDureePeremptionJours() : form.getVersions().stream()
                .filter(v -> Objects.equals(v.getNumero(), versionUtilisee))
                .findFirst()
                .map(VersionFormulation::getDureePeremptionJours)
                .orElse(form.getDureePeremptionJours());
    }

    private void addHistorique(OrdreFabrication of, StatutOf precedent, StatutOf nouveau, String commentaire) {
        if (of.getHistoriqueStatuts() == null) {
            of.setHistoriqueStatuts(new ArrayList<>());
        }
        of.getHistoriqueStatuts().add(HistoriqueStatutOf.builder()
                .statutPrecedent(precedent)
                .statutNouveau(nouveau)
                .date(LocalDateTime.now())
                .commentaire(commentaire)
                .build());
    }
}