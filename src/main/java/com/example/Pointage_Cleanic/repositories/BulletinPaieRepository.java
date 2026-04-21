package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.StatutBulletin;
import com.example.Pointage_Cleanic.entities.BulletinPaie;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface BulletinPaieRepository extends MongoRepository<BulletinPaie, String> {

    List<BulletinPaie> findByEmployeId(String employeId);

    Optional<BulletinPaie> findByEmployeIdAndPeriodeMoisAndPeriodeAnnee(
            String employeId, int mois, int annee);

    List<BulletinPaie> findByPeriodeMoisAndPeriodeAnnee(int mois, int annee);

    List<BulletinPaie> findByPeriodeAnnee(int annee);

    List<BulletinPaie> findByPeriodeAnneeAndEmployeIdOrderByPeriodeMoisAsc(
            int annee, String employeId);

    List<BulletinPaie> findByStatut(StatutBulletin statut);

    List<BulletinPaie> findByPeriodeMoisAndPeriodeAnneeAndStatutIn(
            int mois, int annee, List<StatutBulletin> statuts);

    List<BulletinPaie> findByPeriodeAnneeAndStatutIn(int annee, List<StatutBulletin> statuts);
}