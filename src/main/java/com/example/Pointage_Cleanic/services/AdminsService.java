package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Admins;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminsService {

    private final MongoTemplate mongoTemplate;

    public Admins save(Admins admins) {
        return mongoTemplate.save(admins);
    }

    public List<Admins> getAll() {
        return mongoTemplate.findAll(Admins.class);
    }


    public Admins getByid(String identifiant) {
        Query query = new Query();
        query.addCriteria(Criteria.where("identifiant").is(identifiant));
        return mongoTemplate.findOne(query, Admins.class);
    }
}

