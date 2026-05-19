package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.AjustementMpPayload;
import com.example.Pointage_Cleanic.Dto.productionchimie.MouvementStockChimieDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.ReceptionMpPayload;
import com.example.Pointage_Cleanic.Enum.TypeMouvementChimie;
import com.example.Pointage_Cleanic.Mapper.productionchimie.MouvementStockChimieMapper;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import com.example.Pointage_Cleanic.entities.productionchimie.MouvementStockChimie;
import com.example.Pointage_Cleanic.repositories.productionchimie.MouvementStockChimieRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class MouvementStockChimieService {

    private final MouvementStockChimieRepository repository;
    private final MouvementStockChimieMapper mapper;
    private final MatierePremiereService matierePremiereService;
    private final MongoTemplate mongoTemplate;

    public PageResponse<MouvementStockChimieDto> list(int page, int size, String matierePremiereId,
                                                      TypeMouvementChimie type, String ordreFabricationId,
                                                      LocalDate dateDebut, LocalDate dateFin) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));
        Query query = new Query().with(pageable);

        List<Criteria> criterias = new ArrayList<>();
        if (matierePremiereId != null && !matierePremiereId.isBlank()) {
            criterias.add(Criteria.where("matierePremiereId").is(matierePremiereId));
        }
        if (type != null) {
            criterias.add(Criteria.where("type").is(type));
        }
        if (ordreFabricationId != null && !ordreFabricationId.isBlank()) {
            criterias.add(Criteria.where("ordreFabricationId").is(ordreFabricationId));
        }
        if (dateDebut != null) {
            criterias.add(Criteria.where("date").gte(dateDebut.atStartOfDay()));
        }
        if (dateFin != null) {
            criterias.add(Criteria.where("date").lte(dateFin.atTime(23, 59, 59)));
        }
        if (!criterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));
        }

        List<MouvementStockChimie> results = mongoTemplate.find(query, MouvementStockChimie.class);
        Query countQuery = Query.of(query).limit(-1).skip(-1);
        long total = mongoTemplate.count(countQuery, MouvementStockChimie.class);
        List<MouvementStockChimieDto> content = results.stream().map(mapper::toDto).toList();
        return new PageResponse<>(content, total);
    }

    public MouvementStockChimieDto createReception(ReceptionMpPayload payload) {
        MatierePremiere mp = matierePremiereService.loadOrThrow(payload.getMatierePremiereId());

        MouvementStockChimie mvt = MouvementStockChimie.builder()
                .matierePremiereId(mp.getId())
                .matierePremiereCode(mp.getCode())
                .matierePremiereNom(mp.getNom())
                .unite(mp.getUnite())
                .type(TypeMouvementChimie.ENTREE)
                .quantite(payload.getQuantite())
                .date(LocalDateTime.now())
                .fournisseur(payload.getFournisseur())
                .lotFournisseur(payload.getLotFournisseur())
                .datePeremption(payload.getDatePeremption())
                .commentaire(payload.getCommentaire())
                .build();
        MouvementStockChimie saved = repository.save(mvt);

        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(mp.getId())),
                new Update().inc("quantiteEnStock", payload.getQuantite()).set("updatedAt", LocalDateTime.now()),
                MatierePremiere.class
        );

        return mapper.toDto(saved);
    }

    public MouvementStockChimieDto createAjustement(AjustementMpPayload payload) {
        MatierePremiere mp = matierePremiereService.loadOrThrow(payload.getMatierePremiereId());

        double ancienne = mp.getQuantiteEnStock() == null ? 0.0 : mp.getQuantiteEnStock();
        double nouvelle = payload.getNouvelleQuantite();
        double delta = nouvelle - ancienne;

        MouvementStockChimie mvt = MouvementStockChimie.builder()
                .matierePremiereId(mp.getId())
                .matierePremiereCode(mp.getCode())
                .matierePremiereNom(mp.getNom())
                .unite(mp.getUnite())
                .type(TypeMouvementChimie.AJUSTEMENT)
                .quantite(Math.abs(delta))
                .date(LocalDateTime.now())
                .commentaire(payload.getCommentaire() + " (ancienne=" + ancienne + ", nouvelle=" + nouvelle + ")")
                .build();
        MouvementStockChimie saved = repository.save(mvt);

        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(mp.getId())),
                new Update().set("quantiteEnStock", nouvelle).set("updatedAt", LocalDateTime.now()),
                MatierePremiere.class
        );

        return mapper.toDto(saved);
    }
}