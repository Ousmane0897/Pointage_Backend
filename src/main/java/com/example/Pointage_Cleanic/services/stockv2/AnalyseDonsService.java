package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.SyntheseDonsDto;
import com.example.Pointage_Cleanic.Enum.stockv2.NatureDon;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.LigneBon;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Synthèse des dons (7.5 — /analyse/dons) depuis les bons de sortie DON EFFECTIFS. */
@Service
@RequiredArgsConstructor
public class AnalyseDonsService {

    private final MongoTemplate mongoTemplate;

    public SyntheseDonsDto synthese(LocalDate dateDebut, LocalDate dateFin, NatureDon natureDon,
                                    String beneficiaire, String siteId) {
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les paramètres dateDebut et dateFin sont obligatoires");
        }
        if (dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException("dateFin ne peut pas précéder dateDebut");
        }

        List<BonSortie> bons = chargerDons(dateDebut, dateFin, natureDon, beneficiaire, siteId);

        List<SyntheseDonsDto.LigneDon> lignes = new ArrayList<>();
        Map<String, long[]> parNature = new LinkedHashMap<>();      // [montant, nombre]
        Map<String, long[]> parBeneficiaire = new LinkedHashMap<>(); // [montant, nombre]
        Map<String, Long> parMois = new LinkedHashMap<>();
        long montantTotal = 0;
        java.util.Set<String> beneficiaires = new java.util.HashSet<>();

        for (BonSortie bon : bons) {
            List<LigneBon> lignesBon = bon.getLignes() == null ? List.of() : bon.getLignes();
            double quantiteTotale = lignesBon.stream().mapToDouble(LigneBon::getQuantite).sum();
            long montant = bon.getMontantTotal();
            montantTotal += montant;
            if (bon.getBeneficiaireDon() != null) {
                beneficiaires.add(bon.getBeneficiaireDon());
            }

            lignes.add(SyntheseDonsDto.LigneDon.builder()
                    .bonId(bon.getId())
                    .reference(bon.getReference())
                    .date(bon.getDate())
                    .natureDon(bon.getNatureDon())
                    .beneficiaire(bon.getBeneficiaireDon())
                    .siteSourceNom(bon.getSiteSourceNom())
                    .nbProduits(lignesBon.size())
                    .quantiteTotale(quantiteTotale)
                    .montant(montant)
                    .build());

            if (bon.getNatureDon() != null) {
                long[] n = parNature.computeIfAbsent(bon.getNatureDon().name(), k -> new long[2]);
                n[0] += montant;
                n[1]++;
            }
            if (bon.getBeneficiaireDon() != null) {
                long[] b = parBeneficiaire.computeIfAbsent(bon.getBeneficiaireDon(), k -> new long[2]);
                b[0] += montant;
                b[1]++;
            }
            if (bon.getDate() != null) {
                parMois.merge(YearMonth.from(bon.getDate()).format(AnalyseSupport.MOIS), montant, Long::sum);
            }
        }

        SyntheseDonsDto.Kpis kpis = SyntheseDonsDto.Kpis.builder()
                .montantTotal(montantTotal)
                .nbDons(bons.size())
                .nbBeneficiaires(beneficiaires.size())
                .evolutionPct(evolutionPct(montantTotal, dateDebut, dateFin, natureDon, beneficiaire, siteId))
                .build();

        return SyntheseDonsDto.builder()
                .kpis(kpis)
                .lignes(lignes)
                .repartitionNature(repartition(parNature, false))
                .topBeneficiaires(repartition(parBeneficiaire, true))
                .evolutionMensuelle(evolutionMensuelle(parMois))
                .build();
    }

    private List<BonSortie> chargerDons(LocalDate dateDebut, LocalDate dateFin, NatureDon natureDon,
                                        String beneficiaire, String siteId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("type").is(TypeSortie.DON));
        query.addCriteria(Criteria.where("statut").is(StatutBon.EFFECTIF));
        query.addCriteria(Criteria.where("date").gte(dateDebut).lte(dateFin));
        if (natureDon != null) {
            query.addCriteria(Criteria.where("natureDon").is(natureDon));
        }
        if (AnalyseSupport.notBlank(beneficiaire)) {
            query.addCriteria(Criteria.where("beneficiaireDon").regex(".*" + Pattern.quote(beneficiaire) + ".*", "i"));
        }
        if (AnalyseSupport.notBlank(siteId)) {
            query.addCriteria(Criteria.where("siteSourceId").is(siteId));
        }
        return mongoTemplate.find(query, BonSortie.class);
    }

    private Double evolutionPct(long montantTotal, LocalDate dateDebut, LocalDate dateFin,
                                NatureDon natureDon, String beneficiaire, String siteId) {
        long longueur = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        LocalDate finPrec = dateDebut.minusDays(1);
        LocalDate debutPrec = finPrec.minusDays(longueur - 1);
        long montantPrec = chargerDons(debutPrec, finPrec, natureDon, beneficiaire, siteId).stream()
                .mapToLong(BonSortie::getMontantTotal).sum();
        if (montantPrec <= 0) {
            return null;
        }
        return Math.round((montantTotal - montantPrec) * 100.0 / montantPrec * 100.0) / 100.0;
    }

    private List<SyntheseDonsDto.RepartitionItem> repartition(Map<String, long[]> agg, boolean trierDesc) {
        List<SyntheseDonsDto.RepartitionItem> items = new ArrayList<>();
        agg.forEach((libelle, v) -> items.add(SyntheseDonsDto.RepartitionItem.builder()
                .libelle(libelle).montant(v[0]).nombre(v[1]).build()));
        if (trierDesc) {
            items.sort(Comparator.comparingLong(SyntheseDonsDto.RepartitionItem::getMontant).reversed());
        }
        return items;
    }

    private List<SyntheseDonsDto.PointEvolutionDon> evolutionMensuelle(Map<String, Long> parMois) {
        return parMois.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> SyntheseDonsDto.PointEvolutionDon.builder().mois(e.getKey()).montant(e.getValue()).build())
                .toList();
    }
}
