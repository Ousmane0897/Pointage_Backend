package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilisateursService {

    private final MongoTemplate mongoTemplate;

    public Utilisateur save(Utilisateur utilisateur) {
        return mongoTemplate.save(utilisateur);
    }

    public List<Utilisateur> getAll() {
        return mongoTemplate.findAll(Utilisateur.class);
    }


    public Utilisateur getByid(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        return mongoTemplate.findOne(query, Utilisateur.class);
    }
}

