package com.example.Pointage_Cleanic.services;

import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.entities.Site;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SiteServices {

    private final MongoTemplate mongoTemplate;

    public Site save( Site site) {
        return mongoTemplate.save(site);
    }

    public List<Site> getAll() {
        return mongoTemplate.findAll(Site.class);
    }

    public Site getById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(id));
        return mongoTemplate.findOne(query, Site.class);
    }

}
