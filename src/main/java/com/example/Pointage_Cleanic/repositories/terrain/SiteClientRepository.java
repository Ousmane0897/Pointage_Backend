package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.entities.terrain.SiteClient;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SiteClientRepository extends MongoRepository<SiteClient, String> {

    boolean existsByCode(String code);

    Optional<SiteClient> findByCode(String code);

    List<SiteClient> findByActifTrue();
}