package com.example.Pointage_Cleanic.services.stockv2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SiteSourceCleTest {

    @Test
    void site_du_referentiel_garde_son_id_meme_avec_un_nom() {
        assertThat(SiteSourceCle.cle("site1", "Dakar Plateau")).isEqualTo("site1");
    }

    @Test
    void site_libre_groupe_par_nom_sans_casse_ni_espaces() {
        assertThat(SiteSourceCle.cle(null, "  Chantier   Diamniadio "))
                .isEqualTo(SiteSourceCle.cle("", "chantier diamniadio"))
                .isEqualTo("libre:chantier diamniadio");
    }

    @Test
    void deux_noms_libres_distincts_donnent_deux_cles() {
        assertThat(SiteSourceCle.cle(null, "Chantier A")).isNotEqualTo(SiteSourceCle.cle(null, "Chantier B"));
    }

    @Test
    void ni_id_ni_nom_donne_la_cle_vide() {
        assertThat(SiteSourceCle.cle(null, "   ")).isEqualTo(SiteSourceCle.AUCUN);
        assertThat(SiteSourceCle.cle(null, null)).isEqualTo(SiteSourceCle.AUCUN);
    }

    @Test
    void normaliser_nom_reduit_les_espaces_et_conserve_la_casse() {
        assertThat(SiteSourceCle.normaliserNom("  Chantier   Diamniadio ")).isEqualTo("Chantier Diamniadio");
        assertThat(SiteSourceCle.normaliserNom(" ")).isNull();
    }
}
