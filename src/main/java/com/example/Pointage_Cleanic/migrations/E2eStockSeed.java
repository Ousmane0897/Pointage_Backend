package com.example.Pointage_Cleanic.migrations;

import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.entities.stockv2.CategorieStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import com.example.Pointage_Cleanic.repositories.stockv2.CategorieStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import com.example.Pointage_Cleanic.repositories.stockv2.StockParSiteRepository;
import com.example.Pointage_Cleanic.repositories.terrain.SiteClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Jeu de départ Stock des essais de bout en bout (profil {@code e2e} seulement) : une catégorie,
 * un site, un article par parcours (chaque parcours travaille sur ses propres données) et un
 * stock initial dans l'entrepôt unique ({@code siteId = null}, comme
 * {@link StockEntrepotUniqueMigration}).
 */
@Slf4j
@Component
@Profile("e2e")
@RequiredArgsConstructor
public class E2eStockSeed {

    public static final String CODE_SITE = "E2E-SITE";

    /** Code article → stock initial (0 = pas de solde initial). */
    public static final Map<String, Double> ARTICLES = Map.of(
            "E2E-ENTREE", 0d,
            "E2E-SORTIE", 50d,
            "E2E-ABANDON", 0d,
            "E2E-ANNULATION", 30d,
            "E2E-INVENTAIRE", 20d);

    private final MongoTemplate mongoTemplate;
    private final SiteClientRepository siteClientRepository;
    private final CategorieStockRepository categorieStockRepository;
    private final ProduitStockRepository produitStockRepository;
    private final StockParSiteRepository stockParSiteRepository;

    public void executer() {
        E2eGardeFou.verifierBase(mongoTemplate.getDb().getName());
        LocalDateTime maintenant = LocalDateTime.now();

        siteClientRepository.save(SiteClient.builder()
                .code(CODE_SITE)
                .nom("Site d'essai E2E")
                .raisonSociale("Client d'essai E2E")
                .adresse("1 rue des Essais")
                .ville("Dakar")
                .pays("Sénégal")
                .rayonToleranceM(100)
                .actif(true)
                .createdAt(maintenant)
                .updatedAt(maintenant)
                .build());

        CategorieStock categorie = categorieStockRepository.save(CategorieStock.builder()
                .libelle("Essais E2E")
                .niveau(0)
                .description("Catégorie des essais de bout en bout")
                .createdAt(maintenant)
                .updatedAt(maintenant)
                .build());

        ARTICLES.forEach((code, stockInitial) -> {
            ProduitStock produit = produitStockRepository.save(ProduitStock.builder()
                    .code(code)
                    .libelle("Article " + code)
                    .typeProduit(TypeProduit.CONSOMMABLE)
                    .categorieId(categorie.getId())
                    .unite(UniteStock.PIECE)
                    .seuilAlerte(1)
                    .prixUnitaire(1000)
                    .methodeValorisation(MethodeValorisation.FIXE)
                    .actif(true)
                    .createdAt(maintenant)
                    .updatedAt(maintenant)
                    .build());
            if (stockInitial > 0) {
                stockParSiteRepository.save(StockParSite.builder()
                        .produitId(produit.getId())
                        .siteId(null)
                        .quantite(stockInitial)
                        .dateMaj(maintenant)
                        .build());
            }
        });
        log.info("Profil e2e : jeu de départ Stock créé ({} articles).", ARTICLES.size());
    }
}
