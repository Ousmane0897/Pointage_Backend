package com.example.Pointage_Cleanic.configurations;


import com.mongodb.ReadConcern;
import com.mongodb.TransactionOptions;
import com.mongodb.WriteConcern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Configuration
@EnableMongoAuditing // L’audit permet à Spring Data de renseigner automatiquement certains champs sans code métier
public class MongoConfigurations {

    @Bean
    public MongoMappingContext mongoMappingContext() { //Spring Data MongoDB a besoin de savoir comment mapper (convertir) des objets Java en documents MongoDB (et inversement).
        MongoMappingContext context = new MongoMappingContext();
        // Sans ce SimpleTypeHolder, les types JSR-310 (LocalDate, LocalDateTime…) ne sont
        // pas reconnus comme types simples : l'auditing (@EnableMongoAuditing) tente alors
        // de les introspection comme des entités → InaccessibleObjectException sous Java 21.
        context.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        return context;
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory databaseFactory) { //Le MongoTemplate est une classe que Spring te donne pour interagir directement avec MongoDB.
        return new MongoTemplate(databaseFactory);
    }

    /**
     * Transactions multi-documents (lot 1 / 1a, décision D1). Exige un replica set : mongo:7.0
     * « rs0 » en production (docker-compose.yml), docker-compose.dev.yml en développement, et
     * Testcontainers en test.
     *
     * <p>Lecture et écriture {@code majority} : une transaction validée est durable et ne lit
     * que des données validées. Conventions d'usage : {@code ExecuteurTransactionnel} et
     * CLAUDE.md (section « Transactions »).
     */
    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        TransactionOptions options = TransactionOptions.builder()
                .readConcern(ReadConcern.MAJORITY)
                .writeConcern(WriteConcern.MAJORITY)
                .build();
        return new MongoTransactionManager(databaseFactory, options);
    }

    /** Transactions programmatiques ; préférer {@code ExecuteurTransactionnel}, qui rejoue les conflits. */
    @Bean
    public TransactionTemplate transactionTemplate(MongoTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
