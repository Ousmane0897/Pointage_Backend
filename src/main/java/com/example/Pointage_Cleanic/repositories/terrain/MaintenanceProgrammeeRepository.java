package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.entities.terrain.MaintenanceProgrammee;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface MaintenanceProgrammeeRepository extends MongoRepository<MaintenanceProgrammee, String> {

    List<MaintenanceProgrammee> findByDateProgrammeeBetween(LocalDate debut, LocalDate fin);
}