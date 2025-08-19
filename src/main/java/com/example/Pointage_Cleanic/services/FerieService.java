package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Ferie;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FerieService {

    private final MongoTemplate mongoTemplate;

    public List<Ferie> getAll() {
        return mongoTemplate.findAll(Ferie.class);
    }

    public Ferie save(Ferie ferie) {
        return mongoTemplate.save(ferie);
    }

    public Ferie getById(String date) {
        Query query = new Query();
        query.addCriteria(Criteria.where("date").is(date));
        return mongoTemplate.findOne(query,Ferie.class);
    }


}
