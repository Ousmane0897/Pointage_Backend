package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.BonWorkflowDto;
import com.example.Pointage_Cleanic.Enum.stockv2.SensBon;
import com.example.Pointage_Cleanic.Enum.stockv2.StatutBon;
import com.example.Pointage_Cleanic.entities.stockv2.BonEntree;
import com.example.Pointage_Cleanic.entities.stockv2.BonSortie;
import com.example.Pointage_Cleanic.entities.stockv2.DestinataireBon;
import com.example.Pointage_Cleanic.repositories.stockv2.BonEntreeRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.BonSortieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Vue Kanban unifiée des bons (entrées + sorties), non paginée. */
@Service
@RequiredArgsConstructor
public class WorkflowStockService {

    private final BonEntreeRepository bonEntreeRepository;
    private final BonSortieRepository bonSortieRepository;

    public List<BonWorkflowDto> bons(StatutBon statut, SensBon sens, String q) {
        List<BonWorkflowDto> cartes = new ArrayList<>();
        if (sens == null || sens == SensBon.ENTREE) {
            bonEntreeRepository.findAll().forEach(b -> cartes.add(mapEntree(b)));
        }
        if (sens == null || sens == SensBon.SORTIE) {
            bonSortieRepository.findAll().forEach(b -> cartes.add(mapSortie(b)));
        }

        String filtre = q == null ? null : q.trim().toLowerCase();
        return cartes.stream()
                .filter(c -> statut == null || c.getStatut() == statut)
                .filter(c -> filtre == null || filtre.isBlank() || correspond(c, filtre))
                .sorted(Comparator.comparing(BonWorkflowDto::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean correspond(BonWorkflowDto c, String filtre) {
        return contient(c.getReference(), filtre)
                || contient(c.getSiteNom(), filtre)
                || contient(c.getDestinataireNom(), filtre)
                || contient(c.getLibelleType(), filtre)
                || contient(c.getDemandeurNom(), filtre);
    }

    private boolean contient(String valeur, String filtre) {
        return valeur != null && valeur.toLowerCase().contains(filtre);
    }

    private BonWorkflowDto mapEntree(BonEntree b) {
        return BonWorkflowDto.builder()
                .id(b.getId())
                .reference(b.getReference())
                .sens(SensBon.ENTREE)
                .statut(b.getStatut())
                .date(b.getDate())
                .libelleType(StockLibelles.libelle(b.getType()))
                .siteNom(b.getSiteDestinationNom())
                .demandeurNom(b.getDemandeurNom())
                .validateurNom(b.getValidateurNom())
                .nbLignes(b.getLignes() == null ? 0 : b.getLignes().size())
                .montantTotal(b.getMontantTotal())
                .motifRefus(b.getMotifRefus())
                .build();
    }

    private BonWorkflowDto mapSortie(BonSortie b) {
        return BonWorkflowDto.builder()
                .id(b.getId())
                .reference(b.getReference())
                .sens(SensBon.SORTIE)
                .statut(b.getStatut())
                .date(b.getDate())
                .libelleType(StockLibelles.libelle(b.getType()))
                .siteNom(b.getSiteSourceNom())
                .destinataireNom(nomDestinataire(b.getDestinataire()))
                .demandeurNom(b.getDemandeurNom())
                .validateurNom(b.getValidateurNom())
                .nbLignes(b.getLignes() == null ? 0 : b.getLignes().size())
                .montantTotal(b.getMontantTotal())
                .motifRefus(b.getMotifRefus())
                .build();
    }

    static String nomDestinataire(DestinataireBon d) {
        if (d == null || d.getType() == null) {
            return null;
        }
        return switch (d.getType()) {
            case SITE -> d.getSiteNom();
            case AGENT -> d.getAgentNom();
            case CLIENT -> d.getClientNom();
        };
    }
}
