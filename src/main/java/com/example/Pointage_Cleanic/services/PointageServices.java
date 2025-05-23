package com.example.Pointage_Cleanic.services;


import com.example.Pointage_Cleanic.entities.Pointage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PointageServices {

    private final MongoTemplate mongoTemplate;

    Pointage save(Pointage pointage){

        return mongoTemplate.save(pointage);
    }

    List<Pointage> getAll() {

        return mongoTemplate.findAll(Pointage.class);
    }

}
