package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutSession;
import com.example.Pointage_Cleanic.entities.rh.SessionFormation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface SessionFormationRepository extends MongoRepository<SessionFormation, String> {

    List<SessionFormation> findByFormationId(String formationId);

    List<SessionFormation> findByStatut(StatutSession statut);

    List<SessionFormation> findByFormationIdAndStatut(String formationId, StatutSession statut);

    List<SessionFormation> findByDateDebutBetween(LocalDate debut, LocalDate fin);

    List<SessionFormation> findByDateDebutGreaterThanEqualAndStatut(LocalDate date, StatutSession statut);

    long countByStatut(StatutSession statut);
}