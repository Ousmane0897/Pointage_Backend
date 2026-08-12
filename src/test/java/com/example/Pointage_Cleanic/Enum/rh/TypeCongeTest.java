package com.example.Pointage_Cleanic.Enum.rh;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Règle de décompte portée par le type de congé.
 *
 * <p>Ce test verrouille la règle métier : <b>seul le congé annuel ampute les jours
 * acquis</b>. Il rendra visible tout type ajouté plus tard avec un décompte mal renseigné.
 */
class TypeCongeTest {

    @Test
    void seul_le_conge_annuel_decompte_les_jours_acquis() {
        assertThat(TypeConge.ANNUEL.decompteSoldeAnnuel()).isTrue();

        assertThat(Arrays.stream(TypeConge.values())
                .filter(TypeConge::decompteSoldeAnnuel)
                .toList())
                .containsExactly(TypeConge.ANNUEL);
    }

    @Test
    void ni_le_repos_medical_ni_l_absence_non_justifiee_n_entament_le_solde() {
        assertThat(TypeConge.REPOS_MEDICAL.decompteSoldeAnnuel()).isFalse();
        assertThat(TypeConge.ABSENCE_NON_JUSTIFIEE.decompteSoldeAnnuel()).isFalse();
    }

    @Test
    void chaque_type_porte_un_libelle_lisible() {
        // Le libellé sert aux rendus serveur (e-mails, pointage centralisé) : il doit être
        // accentué et distinct du nom brut de la constante.
        assertThat(TypeConge.values()).allSatisfy(type ->
                assertThat(type.getLibelle()).isNotBlank().isNotEqualTo(type.name()));

        assertThat(TypeConge.REPOS_MEDICAL.getLibelle()).isEqualTo("Repos médical");
        assertThat(TypeConge.ABSENCE_NON_JUSTIFIEE.getLibelle()).isEqualTo("Absence non justifiée");
    }
}
