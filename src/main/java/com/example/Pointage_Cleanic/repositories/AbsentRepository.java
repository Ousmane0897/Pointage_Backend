package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Absent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface AbsentRepository extends MongoRepository<Absent, String> {
}
