package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.StatistiqueCategorieDto;
import com.example.Pointage_Cleanic.Enum.stockv2.SensBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeEntree;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Statistiques d'usage par catégorie (sur les mouvements EFFECTIFS issus de bons). */
@Service
@RequiredArgsConstructor
public class CategorisationStockService {

    private final MongoTemplate mongoTemplate;

    public List<StatistiqueCategorieDto> stats(SensBon sens, LocalDate dateDebut, LocalDate dateFin) {
        if (sens == null) {
            throw new IllegalArgumentException("Le paramètre sens (ENTREE|SORTIE) est obligatoire");
        }
        List<MouvementStock> mouvements = mouvementsBon(sens, dateDebut, dateFin);
        Map<String, Long> prix = mapPrix(mouvements);

        // Agrégation par code de catégorie
        Map<String, double[]> agg = new LinkedHashMap<>(); // code -> [nombre, volume, montant]
        for (MouvementStock m : mouvements) {
            String code = sens == SensBon.ENTREE
                    ? (m.getCategorieEntree() == null ? null : m.getCategorieEntree().name())
                    : (m.getCategorieSortie() == null ? null : m.getCategorieSortie().name());
            if (code == null) {
                continue;
            }
            double[] cumul = agg.computeIfAbsent(code, k -> new double[3]);
            cumul[0] += 1;
            cumul[1] += m.getQuantite();
            cumul[2] += m.getQuantite() * prix.getOrDefault(m.getProduitId(), 0L);
        }

        double volumeTotal = agg.values().stream().mapToDouble(c -> c[1]).sum();

        // Une entrée par code figé du sens (0 si absent).
        List<StatistiqueCategorieDto> result = new ArrayList<>();
        for (String code : codes(sens)) {
            double[] cumul = agg.getOrDefault(code, new double[3]);
            double pourcentage = volumeTotal == 0 ? 0 : (cumul[1] / volumeTotal) * 100;
            result.add(StatistiqueCategorieDto.builder()
                    .code(code)
                    .libelle(libelle(sens, code))
                    .nombre((long) cumul[0])
                    .volume(cumul[1])
                    .montant(Math.round(cumul[2]))
                    .pourcentage(pourcentage)
                    .build());
        }
        return result;
    }

    private List<MouvementStock> mouvementsBon(SensBon sens, LocalDate dateDebut, LocalDate dateFin) {
        Query query = new Query();
        query.addCriteria(Criteria.where("origine").is("BON"));
        query.addCriteria(Criteria.where("type").is(
                sens == SensBon.ENTREE ? TypeMouvement.ENTREE : TypeMouvement.SORTIE));
        if (dateDebut != null && dateFin != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut).lte(dateFin));
        } else if (dateDebut != null) {
            query.addCriteria(Criteria.where("date").gte(dateDebut));
        } else if (dateFin != null) {
            query.addCriteria(Criteria.where("date").lte(dateFin));
        }
        return mongoTemplate.find(query, MouvementStock.class);
    }

    private Map<String, Long> mapPrix(List<MouvementStock> mouvements) {
        Set<String> ids = mouvements.stream().map(MouvementStock::getProduitId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Query q = new Query(Criteria.where("_id").in(ids));
        return mongoTemplate.find(q, ProduitStock.class).stream()
                .collect(Collectors.toMap(ProduitStock::getId, ProduitStock::getPrixUnitaire));
    }

    private List<String> codes(SensBon sens) {
        if (sens == SensBon.ENTREE) {
            return java.util.Arrays.stream(TypeEntree.values()).map(Enum::name).toList();
        }
        return java.util.Arrays.stream(TypeSortie.values()).map(Enum::name).toList();
    }

    private String libelle(SensBon sens, String code) {
        return sens == SensBon.ENTREE
                ? StockLibelles.libelle(TypeEntree.valueOf(code))
                : StockLibelles.libelle(TypeSortie.valueOf(code));
    }
}
