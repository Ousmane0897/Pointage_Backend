package com.example.Pointage_Cleanic.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class MongoTestContainer {

    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void mongoProps(DynamicPropertyRegistry registry) {
        if (!mongo.isRunning()) {
            mongo.start();
        }
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }
}
