package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Ferie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FerieServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private FerieService ferieService;


    // ----------------------------------------------------------
    // test getAll()
    // ----------------------------------------------------------
    @Test
    void testGetAll() {

        when(mongoTemplate.findAll(Ferie.class))
                .thenReturn(List.of(new Ferie(), new Ferie()));

        List<Ferie> result = ferieService.getAll();

        assertEquals(2, result.size());
        verify(mongoTemplate).findAll(Ferie.class);
    }


    // ----------------------------------------------------------
    // test save()
    // ----------------------------------------------------------
    @Test
    void testSave() {

        Ferie f = new Ferie();
        when(mongoTemplate.save(any(Ferie.class))).thenReturn(f);

        Ferie result = ferieService.save(f);

        assertNotNull(result);
        verify(mongoTemplate).save(f);
    }


    // ----------------------------------------------------------
    // test getById()
    // ----------------------------------------------------------
    @Test
    void testGetById() {

        Ferie f = new Ferie();
        f.setDate("01.01.2024");

        when(mongoTemplate.findOne(any(Query.class), eq(Ferie.class)))
                .thenReturn(f);

        Ferie result = ferieService.getById("01.01.2024");

        assertNotNull(result);
        assertEquals("01.01.2024", result.getDate());

        verify(mongoTemplate).findOne(any(Query.class), eq(Ferie.class));
    }

}
