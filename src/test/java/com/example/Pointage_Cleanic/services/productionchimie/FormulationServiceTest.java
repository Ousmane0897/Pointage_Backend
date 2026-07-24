package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.FicheFormulationDto;
import com.example.Pointage_Cleanic.Dto.productionchimie.RestaurerVersionPayload;
import com.example.Pointage_Cleanic.Enum.StatutFormulation;
import com.example.Pointage_Cleanic.Enum.UniteChimie;
import com.example.Pointage_Cleanic.Mapper.productionchimie.FormulationMapper;
import com.example.Pointage_Cleanic.Mapper.productionchimie.FormulationMapperImpl;
import com.example.Pointage_Cleanic.entities.productionchimie.EtapeFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.FicheFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.IngredientFormulation;
import com.example.Pointage_Cleanic.entities.productionchimie.ParametresProductionChimie;
import com.example.Pointage_Cleanic.repositories.productionchimie.FormulationRepository;
import com.example.Pointage_Cleanic.repositories.productionchimie.MatierePremiereRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormulationServiceTest {

    @Mock private FormulationRepository repository;
    @Spy private FormulationMapper mapper = new FormulationMapperImpl();
    @Mock private MongoTemplate mongoTemplate;
    @Spy private FormulationCalculService calculService = new FormulationCalculService();
    @Mock private MatierePremiereRepository matiereRepository;
    @Mock private ParametresProductionChimieService parametresService;

    @InjectMocks
    private FormulationService service;

    private FicheFormulation existante;

    @BeforeEach
    void setUp() {
        lenient().when(matiereRepository.findAllById(any())).thenReturn(new ArrayList<>());
        lenient().when(parametresService.getOrCreate()).thenReturn(
                ParametresProductionChimie.builder().toleranceTotalPct(0.1).build());
        existante = FicheFormulation.builder()
                .id("F-1").code("FORM-1").nom("Eau de Javel")
                .versionCourante(1)
                .ingredients(new ArrayList<>(List.of(
                        IngredientFormulation.builder().matierePremiereId("MP-1").dosage(0.5).unite(UniteChimie.KG).ordre(1).build()
                )))
                .etapes(new ArrayList<>(List.of(
                        EtapeFormulation.builder().ordre(1).libelle("Init").build()
                )))
                .dureePeremptionJours(180)
                .uniteProduction(UniteChimie.L)
                .statut(StatutFormulation.VALIDEE)
                .versions(new ArrayList<>())
                .build();
    }

    @Test
    void update_archive_version_courante_et_incremente() {
        when(repository.findById("F-1")).thenReturn(Optional.of(existante));
        when(repository.save(any(FicheFormulation.class))).thenAnswer(inv -> inv.getArgument(0));

        FicheFormulationDto dto = FicheFormulationDto.builder()
                .code("FORM-1")
                .nom("Eau de Javel")
                .ingredients(List.of(
                        IngredientFormulation.builder().matierePremiereId("MP-1").dosage(0.7).unite(UniteChimie.KG).ordre(1).build()
                ))
                .etapes(List.of(EtapeFormulation.builder().ordre(1).libelle("Init v2").build()))
                .dureePeremptionJours(200)
                .uniteProduction(UniteChimie.L)
                .motif("Ajustement dosage")
                .build();

        service.update("F-1", dto);

        ArgumentCaptor<FicheFormulation> captor = ArgumentCaptor.forClass(FicheFormulation.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        FicheFormulation saved = captor.getValue();

        assertThat(saved.getVersionCourante()).isEqualTo(2);
        assertThat(saved.getVersions()).hasSize(1);
        assertThat(saved.getVersions().get(0).getNumero()).isEqualTo(1);
        assertThat(saved.getVersions().get(0).getMotif()).isEqualTo("Ajustement dosage");
        assertThat(saved.getVersions().get(0).getIngredients().get(0).getDosage()).isEqualTo(0.5);
        assertThat(saved.getIngredients().get(0).getDosage()).isEqualTo(0.7);
        assertThat(saved.getDureePeremptionJours()).isEqualTo(200);
    }

    @Test
    void restaurer_v1_alors_qu_on_est_en_v3() {
        existante.setVersionCourante(3);
        existante.getVersions().add(com.example.Pointage_Cleanic.entities.productionchimie.VersionFormulation.builder()
                .numero(1)
                .ingredients(List.of(
                        IngredientFormulation.builder().matierePremiereId("MP-1").dosage(0.5).unite(UniteChimie.KG).ordre(1).build()
                ))
                .etapes(List.of(EtapeFormulation.builder().ordre(1).libelle("Init v1").build()))
                .dureePeremptionJours(180)
                .build());
        existante.getVersions().add(com.example.Pointage_Cleanic.entities.productionchimie.VersionFormulation.builder()
                .numero(2)
                .ingredients(List.of(
                        IngredientFormulation.builder().matierePremiereId("MP-1").dosage(0.6).unite(UniteChimie.KG).ordre(1).build()
                ))
                .etapes(List.of(EtapeFormulation.builder().ordre(1).libelle("Init v2").build()))
                .dureePeremptionJours(200)
                .build());
        existante.setIngredients(new ArrayList<>(List.of(
                IngredientFormulation.builder().matierePremiereId("MP-1").dosage(0.8).unite(UniteChimie.KG).ordre(1).build()
        )));
        existante.setDureePeremptionJours(300);

        when(repository.findById("F-1")).thenReturn(Optional.of(existante));
        when(repository.save(any(FicheFormulation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.restaurerVersion("F-1", 1, new RestaurerVersionPayload("Retour à la base"));

        ArgumentCaptor<FicheFormulation> captor = ArgumentCaptor.forClass(FicheFormulation.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        FicheFormulation saved = captor.getValue();

        assertThat(saved.getVersionCourante()).isEqualTo(4);
        assertThat(saved.getVersions()).hasSize(3);
        assertThat(saved.getVersions().get(2).getNumero()).isEqualTo(3);
        assertThat(saved.getVersions().get(2).getIngredients().get(0).getDosage()).isEqualTo(0.8);
        assertThat(saved.getIngredients().get(0).getDosage()).isEqualTo(0.5);
        assertThat(saved.getDureePeremptionJours()).isEqualTo(180);
    }
}
