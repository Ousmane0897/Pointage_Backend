package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Ferie;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FerieRepository  extends MongoRepository<Ferie,String> {

}
