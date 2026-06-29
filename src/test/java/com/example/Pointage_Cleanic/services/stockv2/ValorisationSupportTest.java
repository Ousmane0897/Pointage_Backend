package com.example.Pointage_Cleanic.services.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import com.example.Pointage_Cleanic.entities.stockv2.ProduitStock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests unitaires purs du calcul de coût (aucune dépendance Spring/Mongo injectée). */
class ValorisationSupportTest {

    private final ValorisationSupport support = new ValorisationSupport(null, null, null);

    // ----------------------------------------------------------------- CUMP

    @Test
    void cump_moyenne_ponderee() {
        // 100 @ 1000 + 50 @ 1300 = (100000 + 65000) / 150 = 1100
        long cout = support.recalculerCout(MethodeValorisation.CUMP, 1000L, 100, 50, 1300L);
        assertThat(cout).isEqualTo(1100L);
    }

    @Test
    void cump_stock_initial_nul_prend_le_prix_achat() {
        long cout = support.recalculerCout(MethodeValorisation.CUMP, 0L, 0, 20, 1500L);
        assertThat(cout).isEqualTo(1500L);
    }

    @Test
    void cump_stock_negatif_total_non_positif_prend_le_prix_achat() {
        long cout = support.recalculerCout(MethodeValorisation.CUMP, 800L, -10, 5, 2000L);
        assertThat(cout).isEqualTo(2000L);
    }

    @Test
    void cump_quantite_nulle_laisse_le_cout_inchange() {
        long cout = support.recalculerCout(MethodeValorisation.CUMP, 900L, 30, 0, 5000L);
        assertThat(cout).isEqualTo(900L);
    }

    // ----------------------------------------------------------------- DERNIER_PRIX / FIXE

    @Test
    void dernier_prix_prend_le_prix_achat() {
        long cout = support.recalculerCout(MethodeValorisation.DERNIER_PRIX, 1000L, 100, 50, 1300L);
        assertThat(cout).isEqualTo(1300L);
    }

    @Test
    void fixe_ne_recalcule_jamais() {
        long cout = support.recalculerCout(MethodeValorisation.FIXE, 1000L, 100, 50, 1300L);
        assertThat(cout).isEqualTo(1000L);
    }

    @Test
    void methode_null_laisse_le_cout_inchange() {
        long cout = support.recalculerCout(null, 1000L, 100, 50, 1300L);
        assertThat(cout).isEqualTo(1000L);
    }

    // ----------------------------------------------------------------- methodeEffective

    @Test
    void methode_effective_override_produit_prioritaire() {
        ProduitStock p = ProduitStock.builder().methodeValorisation(MethodeValorisation.CUMP).build();
        assertThat(support.methodeEffective(p, MethodeValorisation.DERNIER_PRIX))
                .isEqualTo(MethodeValorisation.CUMP);
    }

    @Test
    void methode_effective_herite_du_global_si_pas_override() {
        ProduitStock p = ProduitStock.builder().build();
        assertThat(support.methodeEffective(p, MethodeValorisation.DERNIER_PRIX))
                .isEqualTo(MethodeValorisation.DERNIER_PRIX);
    }

    @Test
    void methode_effective_fixe_par_defaut_si_rien_defini() {
        ProduitStock p = ProduitStock.builder().build();
        assertThat(support.methodeEffective(p, null)).isEqualTo(MethodeValorisation.FIXE);
    }
}
