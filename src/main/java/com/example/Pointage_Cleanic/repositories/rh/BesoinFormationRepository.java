package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.PrioriteBesoin;
import com.example.Pointage_Cleanic.Enum.rh.StatutBesoin;
import com.example.Pointage_Cleanic.entities.rh.BesoinFormation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BesoinFormationRepository extends MongoRepository<BesoinFormation, String> {

    List<BesoinFormation> findByEmployeId(String employeId);

    List<BesoinFormation> findByDepartement(String departement);

    List<BesoinFormation> findByStatut(StatutBesoin statut);

    List<BesoinFormation> findByPriorite(PrioriteBesoin priorite);

    List<BesoinFormation> findByDepartementAndStatut(String departement, StatutBesoin statut);

    List<BesoinFormation> findByDepartementAndPriorite(String departement, PrioriteBesoin priorite);

    List<BesoinFormation> findByDepartementAndStatutAndPriorite(
            String departement, StatutBesoin statut, PrioriteBesoin priorite);
}