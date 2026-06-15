package com.example.Pointage_Cleanic.services.rh;

import com.example.Pointage_Cleanic.entities.rh.BulletinPaie;
import com.example.Pointage_Cleanic.entities.rh.CategorieProfessionnelle;
import com.example.Pointage_Cleanic.entities.rh.DossierEmploye;
import com.example.Pointage_Cleanic.entities.rh.ParametresPaie;
import com.example.Pointage_Cleanic.Enum.rh.RegimeIpres;
import com.example.Pointage_Cleanic.repositories.rh.CategorieProfessionnelleRepository;
import com.example.Pointage_Cleanic.repositories.rh.DossierEmployeRepository;
import com.example.Pointage_Cleanic.repositories.rh.HeureSupplementaireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests d'orchestration du bulletin : vérifie que la grille est résolue par le
 * {@code categorieCode} de la requête (rattachement manuel) et que les champs
 * perso (numeroIpres/numeroCss/rib/banque) proviennent de la grille.
 */
class CalculPaieOrchestrerTest {

    private DossierEmployeRepository dossierEmployeRepository;
    private CategorieProfessionnelleRepository categorieProfessionnelleRepository;
    private ParametresPaieService parametresPaieService;
    private HeureSupplementaireRepository heureSupplementaireRepository;
    private RecapitulatifMensuelService recapitulatifMensuelService;
    private CalculPaieService service;

    @BeforeEach
    void setup() {
        dossierEmployeRepository = mock(DossierEmployeRepository.class);
        categorieProfessionnelleRepository = mock(CategorieProfessionnelleRepository.class);
        parametresPaieService = mock(ParametresPaieService.class);
        heureSupplementaireRepository = mock(HeureSupplementaireRepository.class);
        recapitulatifMensuelService = mock(RecapitulatifMensuelService.class);

        service = new CalculPaieService(
                dossierEmployeRepository, categorieProfessionnelleRepository,
                parametresPaieService, heureSupplementaireRepository, recapitulatifMensuelService);

        when(dossierEmployeRepository.findById("emp1")).thenReturn(Optional.of(
                DossierEmploye.builder().id("emp1").matricule("M001")
                        .nom("Diop").prenom("Awa").departement("Exploitation").poste("Agent")
                        .build()));

        when(categorieProfessionnelleRepository.findByCode("CADRE")).thenReturn(Optional.of(
                CategorieProfessionnelle.builder()
                        .code("CADRE").salaireBase(300_000L).regimeIpres(RegimeIpres.REGIME_GENERAL)
                        .numeroIpres("IPRES-GRILLE").numeroCss("CSS-GRILLE")
                        .rib("SN0123456789").banque("CBAO")
                        .build()));

        when(parametresPaieService.loadEntity()).thenReturn(ParametresPaie.builder()
                .tauxIpresGeneralSalarie(0.056).tauxIpresGeneralEmployeur(0.084)
                .plafondIpresGeneral(432_000L)
                .tauxIpresComplementaireSalarie(0.024).tauxIpresComplementaireEmployeur(0.036)
                .plafondIpresComplementaire(1_296_000L)
                .tauxCssPrestationsFamilialesEmployeur(0.07).plafondCss(63_000L).tauxAtMpDefaut(0.01)
                .montantTrimfMensuel(900L).joursOuvrablesStandardMois(22)
                .baremeIr(List.of()).build());

        when(recapitulatifMensuelService.getRecapitulatif(4, 2026, "")).thenReturn(List.of(
                RecapitulatifMensuelService.LigneRecapDto.builder()
                        .employeId("emp1").matricule("M001").nomComplet("Awa Diop")
                        .joursOuvrables(22).joursPresents(22).joursAbsents(0).joursConge(0)
                        .totalHeuresSup(0).build()));

        when(heureSupplementaireRepository.findByEmployeIdAndDateBetween(eq("emp1"), any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void orchestrer_lit_les_infos_perso_depuis_la_grille() {
        BulletinPaie bulletin = service.orchestrer("emp1", "CADRE", 4, 2026);

        assertThat(bulletin.getCategorieCode()).isEqualTo("CADRE");
        assertThat(bulletin.getNumeroIpres()).isEqualTo("IPRES-GRILLE");
        assertThat(bulletin.getNumeroCss()).isEqualTo("CSS-GRILLE");
        assertThat(bulletin.getRib()).isEqualTo("SN0123456789");
        assertThat(bulletin.getBanque()).isEqualTo("CBAO");
        // Identité toujours issue de l'employé (DossierEmploye).
        assertThat(bulletin.getMatricule()).isEqualTo("M001");
        assertThat(bulletin.getDepartement()).isEqualTo("Exploitation");
    }

    @Test
    void orchestrer_sans_grille_leve_exception() {
        assertThatThrownBy(() -> service.orchestrer("emp1", "  ", 4, 2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grille salariale");
    }
}
