package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.RapportTableauBordFinancierDto;
import com.example.Pointage_Cleanic.Enum.stockv2.GraviteDerive;
import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeDerive;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.HistoriquePointCout;
import com.example.Pointage_Cleanic.entities.stockv2.MouvementStock;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class TableauBordFinancierIT extends MongoTestContainer {

    @Autowired private TableauBordFinancierService service;
    @Autowired private ProduitStockRepository produitRepository;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), MouvementStock.class);
        mongoTemplate.remove(new Query(), StockParSite.class);
        mongoTemplate.remove(new Query(), ProduitStock.class);
        mongoTemplate.remove(new Query(), HistoriquePointCout.class);
    }

    private String produit(String code, long cout, MethodeValorisation methode) {
        return produitRepository.save(ProduitStock.builder()
                .code(code).libelle("Produit " + code).typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(cout).methodeValorisation(methode)
                .actif(true).build()).getId();
    }

    private void sortie(String produitId, String code, String siteId, double qte, long cout) {
        mongoTemplate.save(MouvementStock.builder()
                .reference("MVT-" + siteId + "-" + qte).produitId(produitId).produitCode(code)
                .produitLibelle("Produit " + code).unite(UniteStock.PIECE).type(TypeMouvement.SORTIE)
                .quantite(qte).siteSourceId(siteId).date(LocalDate.now()).origine("BON")
                .categorieSortie(TypeSortie.CONSOMMATION_INTERNE)
                .coutUnitaireSnapshot(cout).valeurMouvement(Math.round(qte * cout)).build());
    }

    @Test
    void derives_site_gravite_critique_et_attention() {
        String p = produit("D1", 1000L, MethodeValorisation.FIXE);
        // siteA très haut, siteB très bas → écarts > 40 % (CRITIQUE)
        sortie(p, "D1", "siteA", 100, 1000L);
        sortie(p, "D1", "siteB", 1, 1000L);

        RapportTableauBordFinancierDto rapport = service.rapport(
                LocalDate.now().minusDays(5), LocalDate.now(), null, null);

        assertThat(rapport.getDerives()).isNotEmpty();
        assertThat(rapport.getKpis().getNbDerives()).isEqualTo(rapport.getDerives().size());
        assertThat(rapport.getDerives()).anyMatch(d -> d.getType() == TypeDerive.SITE
                && d.getGravite() == GraviteDerive.CRITIQUE);
        assertThat(rapport.getEvolutionValeur()).hasSize(12);
    }

    @Test
    void derive_produit_sur_ecart_de_cout() {
        String p = produit("D2", 1600L, MethodeValorisation.CUMP);
        // Coût précédent 1000, courant 1600 → +60 % → CRITIQUE
        mongoTemplate.save(HistoriquePointCout.builder().produitId(p).cout(1000L)
                .methode(MethodeValorisation.CUMP).date(LocalDate.now().minusDays(3))
                .createdAt(LocalDateTime.now().minusDays(3)).build());
        mongoTemplate.save(HistoriquePointCout.builder().produitId(p).cout(1600L)
                .methode(MethodeValorisation.CUMP).date(LocalDate.now())
                .createdAt(LocalDateTime.now()).build());

        RapportTableauBordFinancierDto rapport = service.rapport(
                LocalDate.now().minusDays(5), LocalDate.now(), null, null);

        assertThat(rapport.getDerives()).anyMatch(d -> d.getType() == TypeDerive.PRODUIT
                && d.getGravite() == GraviteDerive.CRITIQUE
                && d.getValeurActuelle() == 1600L && d.getValeurReference() == 1000L);
    }
}
