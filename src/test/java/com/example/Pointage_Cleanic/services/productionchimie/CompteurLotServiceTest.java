package com.example.Pointage_Cleanic.services.productionchimie;

import com.example.Pointage_Cleanic.entities.productionchimie.CompteurLot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompteurLotServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private CompteurLotService service;

    @Test
    void genererNumero_premier_du_jour() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(CompteurLot.class)))
                .thenReturn(CompteurLot.builder().id("20260519").compteur(1L).build());

        String num = service.genererNumero(LocalDate.of(2026, 5, 19));

        assertThat(num).isEqualTo("20260519-001");
    }

    @Test
    void genererNumero_format_3_chiffres() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(CompteurLot.class)))
                .thenReturn(CompteurLot.builder().id("20260519").compteur(42L).build());

        String num = service.genererNumero(LocalDate.of(2026, 5, 19));

        assertThat(num).isEqualTo("20260519-042");
    }

    @Test
    void genererNumero_au_dela_999() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(CompteurLot.class)))
                .thenReturn(CompteurLot.builder().id("20260519").compteur(1234L).build());

        String num = service.genererNumero(LocalDate.of(2026, 5, 19));

        assertThat(num).isEqualTo("20260519-1234");
    }
}
