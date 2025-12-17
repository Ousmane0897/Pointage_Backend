package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Utilisateur;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UtilisateursServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private UtilisateursService utilisateursService;

    // ---------------------------------------------------
    // 1️⃣ Test save()
    // ---------------------------------------------------
    @Test
    void testSaveUtilisateur() {

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setEmail("test@cleanic.com");

        when(mongoTemplate.save(utilisateur)).thenReturn(utilisateur);

        Utilisateur result = utilisateursService.save(utilisateur);

        assertNotNull(result);
        assertEquals("test@cleanic.com", result.getEmail());
        verify(mongoTemplate).save(utilisateur);
    }

    // ---------------------------------------------------
    // 2️⃣ Test getAll()
    // ---------------------------------------------------
    @Test
    void testGetAllUtilisateurs() {

        when(mongoTemplate.findAll(Utilisateur.class))
                .thenReturn(List.of(new Utilisateur(), new Utilisateur()));

        List<Utilisateur> result = utilisateursService.getAll();

        assertEquals(2, result.size());
        verify(mongoTemplate).findAll(Utilisateur.class);
    }

    // ---------------------------------------------------
    // 3️⃣ Test getByid() → trouvé
    // ---------------------------------------------------
    @Test
    void testGetById_Found() {

        Utilisateur utilisateur = new Utilisateur();
        utilisateur.setId("123");

        when(mongoTemplate.findOne(any(Query.class), eq(Utilisateur.class)))
                .thenReturn(utilisateur);

        Utilisateur result = utilisateursService.getByid("123");

        assertNotNull(result);
        assertEquals("123", result.getId());
        verify(mongoTemplate).findOne(any(Query.class), eq(Utilisateur.class));
    }

    // ---------------------------------------------------
    // 4️⃣ Test getByid() → non trouvé
    // ---------------------------------------------------
    @Test
    void testGetById_NotFound() {

        when(mongoTemplate.findOne(any(Query.class), eq(Utilisateur.class)))
                .thenReturn(null);

        Utilisateur result = utilisateursService.getByid("999");

        assertNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(Utilisateur.class));
    }
}
