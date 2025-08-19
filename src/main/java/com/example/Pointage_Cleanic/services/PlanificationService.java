package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Planification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanificationService {

    private final MongoTemplate mongoTemplate;

    public Planification save(Planification planification) {
        return mongoTemplate.save(planification);
    }

    public List<Planification> getAll() {
        return mongoTemplate.findAll(Planification.class);
    }

    public Planification getBycodeSecret(String codeSecret) {
        Query query = new Query();
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret));
        return mongoTemplate.findOne(query,Planification.class);
    }


    public List<Planification> PlanificationsAVenir(String codeSecret) {
        Date now = new Date();

        Query query = new Query();
        query.addCriteria(
                Criteria.where("codeSecret").is(codeSecret)
                        .and("dateDebut").gt(now)
        );

        return mongoTemplate.find(query, Planification.class);
    }


    public List<Planification> PlanificationsEnCours(String codeSecret) {
        Date now = new Date();

        Query query = new Query();
        query.addCriteria(
                Criteria.where("codeSecret").is(codeSecret)
                        .and("dateDebut").lte(now)
                        .and("dateFin").gte(now)
        );

        return mongoTemplate.find(query, Planification.class);
    }

    public List<Planification> PlanificationsTerminees(String codeSecret) {
        Date now = new Date();

        Query query = new Query();
        query.addCriteria(
                Criteria.where("codeSecret").is(codeSecret)
                        .and("dateFin").lt(now)
        );

        return mongoTemplate.find(query, Planification.class);
    }


}
