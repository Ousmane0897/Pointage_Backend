package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.Plafond;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PlafondRepository extends MongoRepository<Plafond, String> {

    List<Plafond> findByActifTrue();

    List<Plafond> findBySiteIdAndActifTrue(String siteId);
}
