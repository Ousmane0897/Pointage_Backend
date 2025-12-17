package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Site;
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
class SiteServicesTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private SiteServices siteServices;

    // ---------------------------------------------------
    // 1️⃣ Test save()
    // ---------------------------------------------------
    @Test
    void testSaveSite() {

        Site site = new Site();
        site.setId("1");

        when(mongoTemplate.save(site)).thenReturn(site);

        Site result = siteServices.save(site);

        assertNotNull(result);
        assertEquals("1", result.getId());
        verify(mongoTemplate).save(site);
    }

    // ---------------------------------------------------
    // 2️⃣ Test getAll()
    // ---------------------------------------------------
    @Test
    void testGetAllSites() {

        when(mongoTemplate.findAll(Site.class))
                .thenReturn(List.of(new Site(), new Site()));

        List<Site> result = siteServices.getAll();

        assertEquals(2, result.size());
        verify(mongoTemplate).findAll(Site.class);
    }

    // ---------------------------------------------------
    // 3️⃣ Test getById() → trouvé
    // ---------------------------------------------------
    @Test
    void testGetById_Found() {

        Site site = new Site();
        site.setId("ABC");

        when(mongoTemplate.findOne(any(Query.class), eq(Site.class)))
                .thenReturn(site);

        Site result = siteServices.getById("ABC");

        assertNotNull(result);
        assertEquals("ABC", result.getId());
        verify(mongoTemplate).findOne(any(Query.class), eq(Site.class));
    }

    // ---------------------------------------------------
    // 4️⃣ Test getById() → non trouvé
    // ---------------------------------------------------
    @Test
    void testGetById_NotFound() {

        when(mongoTemplate.findOne(any(Query.class), eq(Site.class)))
                .thenReturn(null);

        Site result = siteServices.getById("XYZ");

        assertNull(result);
        verify(mongoTemplate).findOne(any(Query.class), eq(Site.class));
    }
}
