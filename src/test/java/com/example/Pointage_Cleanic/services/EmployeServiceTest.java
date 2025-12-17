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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private EmployeServices employeServices;

    @Test
    void testSave() {
        Employe employe = new Employe();
        when(mongoTemplate.save(any(Employe.class))).thenReturn(employe);

        Employe result = employeServices.save(employe);

        assertNotNull(result);
        verify(mongoTemplate, times(1)).save(employe);
    }

    @Test
    void testGetAll() {
        when(mongoTemplate.findAll(Employe.class)).thenReturn(List.of(new Employe(), new Employe()));

        List<Employe> result = employeServices.getAll();

        assertEquals(2, result.size());
        verify(mongoTemplate, times(1)).findAll(Employe.class);
    }

    @Test
    void testGetByCodeSecret() {
        Employe employe = new Employe();
        when(mongoTemplate.findOne(any(Query.class), eq(Employe.class))).thenReturn(employe);

        Employe result = employeServices.getBycodeSecret("ABC123");

        assertNotNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(Employe.class));
    }

    @Test
    void testCheffeEquipe() {
        when(mongoTemplate.find(any(Query.class), eq(Employe.class)))
                .thenReturn(List.of(new Employe()));

        List<Employe> result = employeServices.CheffeEquipe();

        assertEquals(1, result.size());
        verify(mongoTemplate).find(any(Query.class), eq(Employe.class));
    }

    @Test
    void testEmployeDeplaces() {
        when(mongoTemplate.find(any(Query.class), eq(Employe.class)))
                .thenReturn(List.of(new Employe(), new Employe()));

        List<Employe> result = employeServices.EmployeDeplaces();

        assertEquals(2, result.size());
        verify(mongoTemplate).find(any(Query.class), eq(Employe.class));
    }

    @Test
    void testEmployeesDansUnSite() {
        when(mongoTemplate.find(any(Query.class), eq(Employe.class)))
                .thenReturn(List.of(new Employe()));

        List<Employe> result = employeServices.EmployeesDansUnSite("Dakar");

        assertEquals(1, result.size());
        verify(mongoTemplate).find(any(Query.class), eq(Employe.class));
    }

    @Test
    void testEmployeeRemplacee() {
        Employe employe = new Employe();
        when(mongoTemplate.findOne(any(Query.class), eq(Employe.class)))
                .thenReturn(employe);

        Employe result = employeServices.employeeRemplacee("Ousmane", "Diouf");

        assertNotNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(Employe.class));
    }
}
