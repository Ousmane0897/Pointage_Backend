package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private LoginService loginService;


    // ----------------------------------------------------
    // TEST getById() — Cas où l'utilisateur existe
    // ----------------------------------------------------
    @Test
    void testGetById_found() {

        User user = new User();
        user.setId("123");

        when(mongoTemplate.findOne(any(Query.class), eq(User.class)))
                .thenReturn(user);

        User result = loginService.getById("123");

        assertNotNull(result);
        assertEquals("123", result.getId());
        verify(mongoTemplate).findOne(any(Query.class), eq(User.class));
    }


    // ----------------------------------------------------
    // TEST getById() — Cas où l'utilisateur n'existe pas
    // ----------------------------------------------------
    @Test
    void testGetById_notFound() {

        when(mongoTemplate.findOne(any(Query.class), eq(User.class)))
                .thenReturn(null);

        User result = loginService.getById("999");

        assertNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(User.class));
    }
}
