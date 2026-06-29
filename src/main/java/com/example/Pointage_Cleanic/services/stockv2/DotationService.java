package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ComparatifDotationDto;
import com.example.Pointage_Cleanic.Dto.stockv2.LigneComparatifDto;
import com.example.Pointage_Cleanic.Enum.stockv2.GranularitePlafond;
import com.example.Pointage_Cleanic.Enum.stockv2.SensEcartDotation;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.Plafond;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.PlafondRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Comparatif dotation prévue (plafonds PRODUIT) vs réelle (sorties effectives) sur un mois. */
@Service
@RequiredArgsConstructor
public class DotationService {

    private final PlafondRepository plafondRepository;
    private final ProduitStockRepository produitRepository;
    private final ReferentielSiteService referentielSite;
    private final MongoTemplate mongoTemplate;

    private static class Acc {
        String siteId;
        String produitId;
        double prevu;
        double reel;
    }

    public ComparatifDotationDto comparatif(String mois, String siteId, String produitId) {
        YearMonth ym = parseMois(mois);
        LocalDate debut = ym.atDay(1);
        LocalDate fin = ym.atEndOfMonth();

        Map<String, Acc> accs = new LinkedHashMap<>();

        // Prévu : plafonds PRODUIT actifs
        for (Plafond p : plafondRepository.findByActifTrue()) {
            if (p.getGranularite() != GranularitePlafond.PRODUIT) {
                continue;
            }
            if (siteId != null && !siteId.isBlank() && !siteId.equals(p.getSiteId())) {
                continue;
            }
            if (produitId != null && !produitId.isBlank() && !produitId.equals(p.getCibleId())) {
                continue;
            }
            Acc acc = accs.computeIfAbsent(cle(p.getSiteId(), p.getCibleId()), k -> nouvelAcc(p.getSiteId(), p.getCibleId()));
            acc.prevu += p.getPlafondMensuel();
        }

        // Réel : sorties effectives du mois
        for (MouvementStock m : sortiesDuMois(debut, fin, siteId, produitId)) {
            Acc acc = accs.computeIfAbsent(cle(m.getSiteSourceId(), m.getProduitId()),
                    k -> nouvelAcc(m.getSiteSourceId(), m.getProduitId()));
            acc.reel += m.getQuantite();
        }

        List<LigneComparatifDto> lignes = accs.values().stream().map(this::toLigne).toList();
        double totalPrevu = lignes.stream().mapToDouble(LigneComparatifDto::getPrevu).sum();
        double totalReel = lignes.stream().mapToDouble(LigneComparatifDto::getReel).sum();

        return ComparatifDotationDto.builder()
                .mois(ym.toString())
                .lignes(lignes)
                .totalPrevu(totalPrevu)
                .totalReel(totalReel)
                .build();
    }

    private LigneComparatifDto toLigne(Acc acc) {
        ProduitStock produit = produitRepository.findById(acc.produitId).orElse(null);
        double ecart = acc.reel - acc.prevu;
        double pourcentageEcart = acc.prevu == 0 ? 0 : (ecart / acc.prevu) * 100;
        SensEcartDotation sens = acc.reel > acc.prevu ? SensEcartDotation.SUR_CONSOMMATION
                : (acc.reel < acc.prevu ? SensEcartDotation.SOUS_CONSOMMATION : SensEcartDotation.CONFORME);
        return LigneComparatifDto.builder()
                .siteId(acc.siteId)
                .siteNom(referentielSite.nomDuSite(acc.siteId))
                .produitId(acc.produitId)
                .produitCode(produit == null ? null : produit.getCode())
                .produitLibelle(produit == null ? null : produit.getLibelle())
                .unite(produit == null ? null : produit.getUnite())
                .prevu(acc.prevu)
                .reel(acc.reel)
                .ecart(ecart)
                .pourcentageEcart(pourcentageEcart)
                .sens(sens)
                .build();
    }

    private List<MouvementStock> sortiesDuMois(LocalDate debut, LocalDate fin, String siteId, String produitId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("type").is(TypeMouvement.SORTIE));
        query.addCriteria(Criteria.where("date").gte(debut).lte(fin));
        if (siteId != null && !siteId.isBlank()) {
            query.addCriteria(Criteria.where("siteSourceId").is(siteId));
        }
        if (produitId != null && !produitId.isBlank()) {
            query.addCriteria(Criteria.where("produitId").is(produitId));
        }
        return mongoTemplate.find(query, MouvementStock.class);
    }

    private Acc nouvelAcc(String siteId, String produitId) {
        Acc acc = new Acc();
        acc.siteId = siteId;
        acc.produitId = produitId;
        return acc;
    }

    private String cle(String siteId, String produitId) {
        return siteId + "|" + produitId;
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
