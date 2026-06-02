package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutApplicationPhyto;
import com.example.Pointage_Cleanic.entities.terrain.ApplicationPhyto;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ApplicationPhytoRepository extends MongoRepository<ApplicationPhyto, String> {

    List<ApplicationPhyto> findByDateApplicationBetween(LocalDateTime debut, LocalDateTime fin);

    List<ApplicationPhyto> findByStatut(StatutApplicationPhyto statut);
}