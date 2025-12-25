package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Pointage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PointageRepository extends MongoRepository<Pointage,String> {

    boolean existsByDeviceIdAndTimestampAfter(String deviceId, Instant timestamp);
    Long countByDate(String date);

    long countByDateAndIdIn(String date, List<String> ids);


}
