package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.PointageRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface PointageRequestRepository extends MongoRepository<PointageRequest,String> {

    boolean existsByDeviceIdAndTimestampAfter(String deviceId, Instant timestamp);
}
