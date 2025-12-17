package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Agence;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.projections.AgenceJoursOuvertureProjection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgencesServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AgencesRepository agencesRepository;

    @InjectMocks
    private AgencesServices agencesServices;

    // -------------------------
    // TEST save()
    // -------------------------
    @Test
    void testSave() {
        Agence agence = new Agence();
        when(mongoTemplate.save(any(Agence.class))).thenReturn(agence);

        Agence result = agencesServices.save(agence);

        assertNotNull(result);
        verify(mongoTemplate).save(agence);
    }

    // -------------------------
    // TEST getAll()
    // -------------------------
    @Test
    void testGetAll() {
        when(mongoTemplate.findAll(Agence.class))
                .thenReturn(List.of(new Agence(), new Agence()));

        List<Agence> result = agencesServices.getAll();

        assertEquals(2, result.size());
        verify(mongoTemplate).findAll(Agence.class);
    }

    // -------------------------
    // TEST getAllSiteNames()
    // -------------------------
    @Test
    void testGetAllSiteNames() {

        when(mongoTemplate.findDistinct(any(Query.class),
                eq("nom"), eq("agences"), eq(String.class)))
                .thenReturn(List.of("Dakar", "Thiès"));

        List<String> sites = agencesServices.getAllSiteNames();

        assertEquals(2, sites.size());
        assertTrue(sites.contains("Dakar"));
        verify(mongoTemplate).findDistinct(any(Query.class),
                eq("nom"), eq("agences"), eq(String.class));
    }

    // -------------------------
    // TEST getByNom()
    // -------------------------
    @Test
    void testGetByNom() {
        Agence agence = new Agence();
        agence.setNom("Dakar");

        when(mongoTemplate.findOne(any(Query.class), eq(Agence.class)))
                .thenReturn(agence);

        Agence result = agencesServices.getByNom("Dakar");

        assertNotNull(result);
        assertEquals("Dakar", result.getNom());
        verify(mongoTemplate).findOne(any(Query.class), eq(Agence.class));
    }

    // -------------------------
    // TEST getNumberofEmployeesInOneAgence()
    // -------------------------
    @Test
    void testGetNumberOfEmployeesInOneAgence() {
        when(mongoTemplate.count(any(Query.class), eq(Employe.class)))
                .thenReturn(5L);

        Integer result = agencesServices.getNumberofEmployeesInOneAgence("Dakar");

        assertEquals(5, result);
        verify(mongoTemplate).count(any(Query.class), eq(Employe.class));
    }

    // -------------------------
    // TEST getMaxNumberOfEmployeesInOneAgence()
    // -------------------------
    @Test
    void testGetMaxNumberOfEmployeesInOneAgence() {
        Agence agence = new Agence();
        agence.setNombreAgentsMaximum(20);

        when(mongoTemplate.findOne(any(Query.class), eq(Agence.class)))
                .thenReturn(agence);

        Integer result = agencesServices.getMaxNumberOfEmployeesInOneAgence("Dakar");

        assertEquals(20, result);
        verify(mongoTemplate).findOne(any(Query.class), eq(Agence.class));
    }

    @Test
    void testGetMaxNumberOfEmployeesInOneAgence_returnsNullWhenNotFound() {

        when(mongoTemplate.findOne(any(Query.class), eq(Agence.class)))
                .thenReturn(null);

        Integer result = agencesServices.getMaxNumberOfEmployeesInOneAgence("Unknown");

        assertNull(result);
    }


    // -------------------------
    // TEST EmployeeParAgence()
    // -------------------------
    @Test
    void testEmployeeParAgence() {

        when(mongoTemplate.find(any(Query.class), eq(Employe.class)))
                .thenReturn(List.of(new Employe()));

        List<Employe> result = agencesServices.EmployeeParAgence("Dakar");

        assertEquals(1, result.size());
        verify(mongoTemplate).find(any(Query.class), eq(Employe.class));
    }

    // -------------------------
    // TEST EmployeeDeplacee()
    // -------------------------
    @Test
    void testEmployeeDeplacee() {
        Employe emp = new Employe();

        when(mongoTemplate.findOne(any(Query.class), eq(Employe.class)))
                .thenReturn(emp);

        Employe result = agencesServices.EmployeeDeplacee("Dakar");

        assertNotNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(Employe.class));
    }

    // -------------------------
    // TEST EmployeeRemplacee()
    // -------------------------
    @Test
    void testEmployeeRemplacee() {
        Employe emp = new Employe();

        when(mongoTemplate.findOne(any(Query.class), eq(Employe.class)))
                .thenReturn(emp);

        Employe result = agencesServices.EmployeeRemplacee("Touba");

        assertNotNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(Employe.class));
    }


    // -------------------------
    // TEST getJoursOuvertureByNom()
    // -------------------------
    @Test
    void testGetJoursOuvertureByNom() {

        AgenceJoursOuvertureProjection projection =
                mock(AgenceJoursOuvertureProjection.class);

        when(projection.getJoursOuverture())
                .thenReturn("Lun-Ven");

        when(agencesRepository.findJoursOuvertureByNom("Dakar"))
                .thenReturn(Optional.of(projection));

        Optional<String> result =
                agencesServices.getJoursOuvertureByNom("Dakar");

        assertTrue(result.isPresent());
        assertEquals("Lun-Ven", result.get());

        verify(agencesRepository).findJoursOuvertureByNom("Dakar");
    }

    @Test
    void testGetJoursOuvertureByNom_returnsEmptyIfNotFound() {

        when(agencesRepository.findJoursOuvertureByNom("???"))
                .thenReturn(Optional.empty());

        Optional<String> result =
                agencesServices.getJoursOuvertureByNom("???");

        assertTrue(result.isEmpty());
    }


}
