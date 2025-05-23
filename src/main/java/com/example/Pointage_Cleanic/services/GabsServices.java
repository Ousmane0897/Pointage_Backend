package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Gab;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GabsServices {

    private final MongoTemplate mongoTemplate;

    public Gab save(Gab gab) {
        return mongoTemplate.save(gab);
    }

    public List<Gab> getAll() {
        return mongoTemplate.findAll(Gab.class);
    }

    public Gab getById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(id));
        return mongoTemplate.findOne(query,Gab.class);
    }


}
