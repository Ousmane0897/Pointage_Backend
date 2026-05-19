package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.Dto.productionchimie.ControleQualiteDto;
import com.example.Pointage_Cleanic.Enum.DecisionControle;
import com.example.Pointage_Cleanic.Enum.StatutControleLot;
import com.example.Pointage_Cleanic.Enum.StatutStockLot;
import com.example.Pointage_Cleanic.Mapper.productionchimie.ControleQualiteMapper;
import com.example.Pointage_Cleanic.Mapper.productionchimie.ControleQualiteMapperImpl;
import com.example.Pointage_Cleanic.entities.productionchimie.ControleQualite;
import com.example.Pointage_Cleanic.entities.productionchimie.Lot;
import com.example.Pointage_Cleanic.exception.ControleQualiteInvalideException;
import com.example.Pointage_Cleanic.repositories.productionchimie.ControleQualiteRepository;
import com.example.Pointage_Cleanic.repositories.productionchimie.GrilleControleRepository;
import com.example.Pointage_Cleanic.repositories.productionchimie.LotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControleQualiteServiceTest {

    @Mock private ControleQualiteRepository repository;
    @Spy private ControleQualiteMapper mapper = new ControleQualiteMapperImpl();
    @Mock private LotRepository lotRepository;
    @Mock private GrilleControleRepository grilleRepository;
    @Mock private MongoTemplate mongoTemplate;

    @InjectMocks
    private ControleQualiteService service;

    private Lot lot;

    @BeforeEach
    void setUp() {
        lot = Lot.builder().id("L-1").numero("20260519-001").produitNom("Javel")
                .statutControle(StatutControleLot.EN_ATTENTE_CONTROLE).statutStock(StatutStockLot.EN_PRODUCTION).build();
    }

    @Test
    void rejet_sans_commentaire_leve_400() {
        ControleQualiteDto dto = ControleQualiteDto.builder()
                .lotId("L-1").dateControle(LocalDateTime.now()).decision(DecisionControle.REJET).build();

        assertThatThrownBy(() -> service.create(dto, null))
                .isInstanceOf(ControleQualiteInvalideException.class)
                .hasMessageContaining("Commentaire obligatoire");
    }

    @Test
    void valide_met_lot_en_stock() throws Exception {
        when(lotRepository.findById("L-1")).thenReturn(Optional.of(lot));
        when(repository.save(any(ControleQualite.class))).thenAnswer(inv -> {
            ControleQualite c = inv.getArgument(0);
            c.setId("CQ-1");
            return c;
        });

        ControleQualiteDto dto = ControleQualiteDto.builder()
                .lotId("L-1").dateControle(LocalDateTime.now()).decision(DecisionControle.VALIDE).build();

        service.create(dto, null);

        ArgumentCaptor<Lot> captor = ArgumentCaptor.forClass(Lot.class);
        org.mockito.Mockito.verify(lotRepository).save(captor.capture());
        Lot saved = captor.getValue();
        assertThat(saved.getStatutControle()).isEqualTo(StatutControleLot.VALIDE);
        assertThat(saved.getStatutStock()).isEqualTo(StatutStockLot.EN_STOCK);
        assertThat(saved.getControleQualiteId()).isEqualTo("CQ-1");
    }

    @Test
    void rejet_avec_commentaire_bloque_lot() throws Exception {
        when(lotRepository.findById("L-1")).thenReturn(Optional.of(lot));
        when(repository.save(any(ControleQualite.class))).thenAnswer(inv -> {
            ControleQualite c = inv.getArgument(0);
            c.setId("CQ-1");
            return c;
        });

        ControleQualiteDto dto = ControleQualiteDto.builder()
                .lotId("L-1").dateControle(LocalDateTime.now())
                .decision(DecisionControle.REJET).commentaire("pH hors plage")
                .build();

        service.create(dto, null);

        ArgumentCaptor<Lot> captor = ArgumentCaptor.forClass(Lot.class);
        org.mockito.Mockito.verify(lotRepository).save(captor.capture());
        Lot saved = captor.getValue();
        assertThat(saved.getStatutControle()).isEqualTo(StatutControleLot.REJETE);
        assertThat(saved.getStatutStock()).isEqualTo(StatutStockLot.BLOQUE);
    }
}
