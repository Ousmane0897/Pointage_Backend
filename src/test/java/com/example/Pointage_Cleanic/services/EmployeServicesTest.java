package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Employe;
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
class EmployeServicesTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private EmployeServices employeServices;

    // ----------------------------------------------------------
    // save()
    // ----------------------------------------------------------
    @Test
    void testSave() {

        Employe employe = new Employe();
        when(mongoTemplate.save(any(Employe.class))).thenReturn(employe);

        Employe result = employeServices.save(employe);

        assertNotNull(result);
        verify(mongoTemplate).save(employe);
    }

    // ----------------------------------------------------------
    // getAll()
    // ----------------------------------------------------------
    @Test
    void testGetAll() {

        when(mongoTemplate.findAll(Employe.class))
                .thenReturn(List.of(new Employe(), new Employe()));

        List<Employe> result = employeServices.getAll();

        assertEquals(2, result.size());
        verify(mongoTemplate).findAll(Employe.class);
    }

    // ----------------------------------------------------------
    // getBycodeSecret()
    // ----------------------------------------------------------
    @Test
    void testGetBycodeSecret() {

        Employe employe = new Employe();
        employe.setCodeSecret("ABC");

        when(mongoTemplate.findOne(any(Query.class), eq(Employe.class)))
                .thenReturn(employe);

        Employe result = employeServices.getBycodeSecret("ABC");

        assertNotNull(result);
        assertEquals("ABC", result.getCodeSecret());
        verify(mongoTemplate).findOne(any(Query.class), eq(Employe.class));
    }

    // ----------------------------------------------------------
    // CheffeEquipe()
    // ----------------------------------------------------------
    @Test
    void testCheffeEquipe() {

        when(mongoTemplate.find(any(Query.class), eq(Employe.class)))
                .thenReturn(List.of(new Employe()));

        List<Employe> result = employeServices.CheffeEquipe();

        assertEquals(1, result.size());
        verify(mongoTemplate).find(any(Query.class), eq(Employe.class));
    }

    // ----------------------------------------------------------
    // EmployeDeplaces()
    // ----------------------------------------------------------
    @Test
    void testEmployeDeplaces() {

        when(mongoTemplate.find(any(Query.class), eq(Employe.class)))
                .thenReturn(List.of(new Employe(), new Employe()));

        List<Employe> result = employeServices.EmployeDeplaces();

        assertEquals(2, result.size());
        verify(mongoTemplate).find(any(Query.class), eq(Employe.class));
    }

    // ----------------------------------------------------------
    // EmployeesDansUnSite()
    // ----------------------------------------------------------
    @Test
    void testEmployeesDansUnSite() {

        when(mongoTemplate.find(any(Query.class), eq(Employe.class)))
                .thenReturn(List.of(new Employe()));

        List<Employe> result = employeServices.EmployeesDansUnSite("Dakar");

        assertEquals(1, result.size());
        verify(mongoTemplate).find(any(Query.class), eq(Employe.class));
    }

    // ----------------------------------------------------------
    // employeeRemplacee()
    // ----------------------------------------------------------
    @Test
    void testEmployeeRemplacee() {

        Employe emp = new Employe();
        emp.setPrenom("Ousmane");
        emp.setNom("Diouf");

        when(mongoTemplate.findOne(any(Query.class), eq(Employe.class)))
                .thenReturn(emp);

        Employe result = employeServices.employeeRemplacee("Ousmane", "Diouf");

        assertNotNull(result);
        assertEquals("Ousmane", result.getPrenom());
        assertEquals("Diouf", result.getNom());

        verify(mongoTemplate).findOne(any(Query.class), eq(Employe.class));
    }
}
