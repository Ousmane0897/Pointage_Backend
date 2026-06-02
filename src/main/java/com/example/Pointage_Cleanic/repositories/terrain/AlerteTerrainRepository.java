package com.example.Pointage_Cleanic.repositories.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutAlerte;
import com.example.Pointage_Cleanic.Enum.terrain.TypeAlerteTerrain;
import com.example.Pointage_Cleanic.entities.terrain.AlerteTerrain;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AlerteTerrainRepository extends MongoRepository<AlerteTerrain, String> {

    List<AlerteTerrain> findByStatutIn(List<StatutAlerte> statuts);

    List<AlerteTerrain> findByTypeAndEmployeIdAndAffectationId(
            TypeAlerteTerrain type, String employeId, String affectationId);

    List<AlerteTerrain> findByDateEvenementBetween(LocalDateTime debut, LocalDateTime fin);

    long countByStatut(StatutAlerte statut);
}