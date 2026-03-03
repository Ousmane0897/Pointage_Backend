package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Pointage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface PointageRepository extends MongoRepository<Pointage,String> {

    boolean existsByDeviceIdAndTimestampAfter(String deviceId, Instant timestamp);
    Long countByDate(LocalDate date);

    long countByDateAndIdIn(LocalDate date, List<String> ids);

    List<Pointage> findAllByDate(LocalDate date);

    Page<Pointage> findByDateNotOrderByTimestampDesc(
            String date,
            Pageable pageable
    );


}
