package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Agence;
import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.repositories.AgencesRepository;
import com.example.Pointage_Cleanic.repositories.projections.AgenceJoursOuvertureProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AgencesServices {

    private final MongoTemplate mongoTemplate;
    private final AgencesRepository agencesRepository;

    public Agence save(Agence agence) {
        return mongoTemplate.save(agence);
    }

    public List<Agence> getAll() {
        return mongoTemplate.findAll(Agence.class);
    }

    public List<String> getAllSiteNames() {
        return mongoTemplate
                .findDistinct(new Query(),"nom","agences", String.class);
    }

    public Agence getByNom(String nom) {
        Query query = new Query();
        query.addCriteria(Criteria.where("nom").is(nom));
        return mongoTemplate.findOne(query,Agence.class);
    }

    public Integer getNumberofEmployeesInOneAgence(String nomAgence) {

        Query query = new Query();
        query.addCriteria(Criteria.where("site").is(nomAgence));
        return Math.toIntExact(mongoTemplate.count(query, Employe.class));
    }

    public Integer getMaxNumberOfEmployeesInOneAgence(String nomAgence) {

        Query query = new Query();
        query.addCriteria(Criteria.where("nom").is(nomAgence));
        Agence agence =  mongoTemplate.findOne(query, Agence.class);
        return agence != null ? agence.getNombreAgentsMaximum() : null;
    }

    public List<Employe> EmployeeParAgence(String site) {
        Query query = new Query();
        query.addCriteria(Criteria.where("site").is(site));
        return mongoTemplate.find(query,Employe.class);
    }


    //Trouver l'agent qui a été déplacée
    public Employe EmployeeDeplacee(String site) {
        Query query = new Query();
        query.addCriteria(Criteria.where("site").is(site).and("deplacement").is(true));
        return mongoTemplate.findOne(query,Employe.class);
    }

    //Trouver l'agent qui a été remplacée
    public Employe EmployeeRemplacee(String site) {
        Query query = new Query();
        query.addCriteria(Criteria.where("site").is(site).and("remplacement").is(true));
        return mongoTemplate.findOne(query,Employe.class);
    }



    public Optional<String> getJoursOuvertureByNom(String nomAgence) {
        return agencesRepository
                .findJoursOuvertureByNom(nomAgence)
                .map(AgenceJoursOuvertureProjection::getJoursOuverture);
    }

}
