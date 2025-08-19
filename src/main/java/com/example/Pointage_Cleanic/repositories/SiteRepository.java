package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Site;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteRepository extends MongoRepository<Site,String> {
}
