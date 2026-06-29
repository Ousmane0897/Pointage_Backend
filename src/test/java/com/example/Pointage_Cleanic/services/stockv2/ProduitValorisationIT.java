package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Dto.stockv2.ProduitDto;
import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeProduit;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import com.example.Pointage_Cleanic.repositories.stockv2.ProduitStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.mail.host=localhost",
        "spring.mail.port=25",
        "jwt.secret=test_secret_at_least_32_characters_long_xyz"
})
class ProduitValorisationIT extends MongoTestContainer {

    @Autowired private ProduitStockService service;
    @Autowired private ProduitStockRepository repository;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void setup() {
        mongoTemplate.remove(new Query(), ProduitStock.class);
    }

    private String produit() {
        return repository.save(ProduitStock.builder()
                .code("P1").libelle("Savon").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L).actif(true).build()).getId();
    }

    @Test
    void patch_ecrit_methode_et_prix_vente() {
        String id = produit();
        service.definirMethodeValorisation(id, MethodeValorisation.CUMP);
        service.definirPrixVente(id, 1500L);

        ProduitStock p = repository.findById(id).orElseThrow();
        assertThat(p.getMethodeValorisation()).isEqualTo(MethodeValorisation.CUMP);
        assertThat(p.getPrixVente()).isEqualTo(1500L);
    }

    @Test
    void le_formulaire_73_ne_peut_pas_ecraser_les_champs_financiers() throws Exception {
        String id = produit();
        service.definirMethodeValorisation(id, MethodeValorisation.CUMP);
        service.definirPrixVente(id, 1500L);

        // Tentative via le formulaire produit 7.3 (mapper) de modifier les champs financiers.
        ProduitDto dto = ProduitDto.builder()
                .code("P1").libelle("Savon renommé").typeProduit(TypeProduit.CONSOMMABLE)
                .unite(UniteStock.PIECE).seuilAlerte(5).prixUnitaire(1000L)
                .methodeValorisation(MethodeValorisation.DERNIER_PRIX).prixVente(99L)
                .build();
        service.update(id, dto, null, null, false, false);

        ProduitStock p = repository.findById(id).orElseThrow();
        assertThat(p.getLibelle()).isEqualTo("Savon renommé");          // champ 7.3 modifiable
        assertThat(p.getMethodeValorisation()).isEqualTo(MethodeValorisation.CUMP);  // inchangé
        assertThat(p.getPrixVente()).isEqualTo(1500L);                  // inchangé
    }
}
