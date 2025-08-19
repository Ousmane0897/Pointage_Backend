package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Employe;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeServices {


    private final MongoTemplate mongoTemplate;

    public Employe save(Employe employe) {
        return mongoTemplate.save(employe);
    }

    public List<Employe> getAll() {
        return mongoTemplate.findAll(Employe.class);
    }

    public Employe getBycodeSecret(String codeSecret) {
        Query query = new Query();
        query.addCriteria(Criteria.where("codeSecret").is(codeSecret));
        return mongoTemplate.findOne(query,Employe.class);
    }

    public List<Employe> CheffeEquipe() {
        Query query = new Query();
        query.addCriteria(Criteria.where("statut").is("Cheffe d'équipe"));
        return mongoTemplate.find(query,Employe.class);
    }

}
