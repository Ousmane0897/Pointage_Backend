package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.config.MongoTestContainer;
import com.example.Pointage_Cleanic.entities.Pointage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataMongoTest
class PointageRepositoryTest extends MongoTestContainer {

    @Autowired
    private PointageRepository repository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void resetDatabase() {
        mongoTemplate.getDb().drop();
    }


    @Test
    void should_detect_recent_device_usage() {

        Pointage p = new Pointage();
        p.setDeviceId("DEV-1");
        p.setTimestamp(Instant.now());
        p.setDate(LocalDate.parse("10/12/2025"));

        repository.save(p);

        boolean exists = repository.existsByDeviceIdAndTimestampAfter(
                "DEV-1",
                Instant.now().minusSeconds(60));

        assertTrue(exists);
    }

    @Test
    void should_count_by_date() {

        Pointage p1 = new Pointage();
        p1.setDate(LocalDate.parse("10/12/2025"));

        Pointage p2 = new Pointage();
        p2.setDate(LocalDate.parse("10/12/2025"));

        repository.saveAll(List.of(p1, p2));

        Long count = repository.countByDate(LocalDate.parse("10/12/2025"));

        assertEquals(2L, count);
    }

    @Test
    void should_count_by_date_and_id_list() {

        Pointage p1 = new Pointage();
        p1.setDate(LocalDate.parse("10/12/2025"));

        Pointage p2 = new Pointage();
        p2.setDate(LocalDate.parse("10/12/2025"));

        Pointage saved1 = repository.save(p1);
        Pointage saved2 = repository.save(p2);

        long count = repository.countByDateAndIdIn(
                LocalDate.parse("10/12/2025"),
                List.of(saved1.getId(), saved2.getId()));

        assertEquals(2L, count);
    }
}
