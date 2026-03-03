package com.example.Pointage_Cleanic.configurations;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

@Configuration
@EnableMongoAuditing // L’audit permet à Spring Data de renseigner automatiquement certains champs sans code métier
public class MongoConfigurations {

    @Bean
    public MongoMappingContext mongoMappingContext() { //Spring Data MongoDB a besoin de savoir comment mapper (convertir) des objets Java en documents MongoDB (et inversement).
        return new MongoMappingContext();
    }

    @Bean
    public MongoTemplate mongoTemplate(MongoDatabaseFactory databaseFactory) { //Le MongoTemplate est une classe que Spring te donne pour interagir directement avec MongoDB.
        return new MongoTemplate(databaseFactory);
    }


}
