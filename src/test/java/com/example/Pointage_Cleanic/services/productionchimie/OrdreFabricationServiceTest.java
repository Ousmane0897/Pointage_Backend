package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Enum.StatutOf;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.example.Pointage_Cleanic.Mapper.productionchimie.OrdreFabricationMapper;
import com.example.Pointage_Cleanic.Mapper.productionchimie.OrdreFabricationMapperImpl;
import com.example.Pointage_Cleanic.entities.productionchimie.ConsommationMp;
import com.example.Pointage_Cleanic.entities.productionchimie.FicheFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.IngredientFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.MatierePremiere;
import com.example.Pointage_Cleanic.entities.productionchimie.MouvementStockChimie;
import com.example.Pointage_Cleanic.entities.productionchimie.OrdreFabrication;
import com.example.Pointage_Cleanic.exception.StockChimieInsuffisantException;
import com.example.Pointage_Cleanic.exception.TransitionOfInterditeException;
import com.example.Pointage_Cleanic.repositories.productionchimie.LotRepository;
import com.example.Pointage_Cleanic.repositories.productionchimie.MouvementStockChimieRepository;
import com.example.Pointage_Cleanic.repositories.productionchimie.OrdreFabricationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdreFabricationServiceTest {

    @Mock private OrdreFabricationRepository repository;
    @Spy private OrdreFabricationMapper mapper = new OrdreFabricationMapperImpl();
    @Mock private FormulationService formulationService;
    @Mock private MatierePremiereService matierePremiereService;
    @Mock private MouvementStockChimieRepository mouvementRepository;
    @Mock private LotRepository lotRepository;
    @Mock private CompteurOfService compteurOfService;
    @Mock private CompteurLotService compteurLotService;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks
    private OrdreFabricationService service;

    private OrdreFabrication ofEnAttente;
    private FicheFormulation formulation;
    private MatierePremiere mpSoude;

    @BeforeEach
    void setUp() {
        IngredientFormulation ing = IngredientFormulation.builder()
                .matierePremiereId("MP-1").dosage(2.0).unite(UniteChimie.KG).ordre(1).build();
        formulation = FicheFormulation.builder()
                .id("F-1").code("FORM-1").nom("Test").versionCourante(1)
                .ingredients(List.of(ing))
                .build();
        mpSoude = MatierePremiere.builder().id("MP-1").code("MP-1").nom("Soude").unite(UniteChimie.KG).quantiteEnStock(100.0).build();
        ofEnAttente = OrdreFabrication.builder()
                .id("OF-1").numero("OF-20260519-001").produitNom("X")
                .formulationId("F-1").formulationVersion(1)
                .quantiteCible(10.0).uniteCible(UniteChimie.L)
                .statut(StatutOf.EN_ATTENTE)
                .build();
    }

    @Test
    void lancer_mp_suffisantes_cree_sortie_et_passe_en_cours() {
        when(repository.findById("OF-1")).thenReturn(Optional.of(ofEnAttente));
        when(formulationService.loadOrThrow("F-1")).thenReturn(formulation);
        when(matierePremiereService.loadOrThrow("MP-1")).thenReturn(mpSoude);
        when(mouvementRepository.save(any(MouvementStockChimie.class))).thenAnswer(inv -> {
            MouvementStockChimie m = inv.getArgument(0);
            m.setId("MVT-1");
            return m;
        });
        when(repository.save(any(OrdreFabrication.class))).thenAnswer(inv -> inv.getArgument(0));

        service.lancer("OF-1", null);

        verify(mouvementRepository, times(1)).save(any(MouvementStockChimie.class));
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(MatierePremiere.class));

        ArgumentCaptor<OrdreFabrication> captor = ArgumentCaptor.forClass(OrdreFabrication.class);
        verify(repository).save(captor.capture());
        OrdreFabrication saved = captor.getValue();
        assertThat(saved.getStatut()).isEqualTo(StatutOf.EN_COURS);
        assertThat(saved.getDateLancementEffective()).isNotNull();
        assertThat(saved.getConsommationMp()).hasSize(1);
        assertThat(saved.getConsommationMp().get(0).getMouvementStockId()).isEqualTo("MVT-1");
        assertThat(saved.getConsommationMp().get(0).getQuantiteTheorique()).isEqualTo(20.0);
    }

    @Test
    void lancer_mp_insuffisantes_leve_409_et_aucune_sortie_creee() {
        mpSoude.setQuantiteEnStock(5.0);
        when(repository.findById("OF-1")).thenReturn(Optional.of(ofEnAttente));
        when(formulationService.loadOrThrow("F-1")).thenReturn(formulation);
        when(matierePremiereService.loadOrThrow("MP-1")).thenReturn(mpSoude);

        assertThatThrownBy(() -> service.lancer("OF-1", null))
                .isInstanceOf(StockChimieInsuffisantException.class)
                .hasMessageContaining("insuffisante");

        verify(mouvementRepository, never()).save(any());
        verify(repository, never()).save(any());
    }

    @Test
    void lancer_depuis_termine_leve_transition_interdite() {
        ofEnAttente.setStatut(StatutOf.TERMINE);
        when(repository.findById("OF-1")).thenReturn(Optional.of(ofEnAttente));

        assertThatThrownBy(() -> service.lancer("OF-1", null))
                .isInstanceOf(TransitionOfInterditeException.class);
    }

    @Test
    void annuler_depuis_en_cours_rollback_les_sorties() {
        ofEnAttente.setStatut(StatutOf.EN_COURS);
        ofEnAttente.setConsommationMp(List.of(
                ConsommationMp.builder()
                        .matierePremiereId("MP-1").quantiteTheorique(20.0)
                        .mouvementStockId("MVT-1").unite(UniteChimie.KG)
                        .build()
        ));
        when(repository.findById("OF-1")).thenReturn(Optional.of(ofEnAttente));
        when(repository.save(any(OrdreFabrication.class))).thenAnswer(inv -> inv.getArgument(0));

        service.annuler("OF-1", new com.example.Pointage_Cleanic.Dto.productionchimie.AnnulerOfPayload("Test rollback"));

        verify(mouvementRepository, times(1)).save(any(MouvementStockChimie.class));
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), any(Update.class), eq(MatierePremiere.class));

        ArgumentCaptor<OrdreFabrication> captor = ArgumentCaptor.forClass(OrdreFabrication.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatut()).isEqualTo(StatutOf.ANNULE);
    }

    @Test
    void annuler_depuis_en_attente_pas_de_rollback() {
        when(repository.findById("OF-1")).thenReturn(Optional.of(ofEnAttente));
        when(repository.save(any(OrdreFabrication.class))).thenAnswer(inv -> inv.getArgument(0));

        service.annuler("OF-1", new com.example.Pointage_Cleanic.Dto.productionchimie.AnnulerOfPayload("Plus besoin"));

        verify(mouvementRepository, never()).save(any());
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(MatierePremiere.class));
    }
}
