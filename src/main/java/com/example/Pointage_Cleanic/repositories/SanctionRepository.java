package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.Enum.StatutSanction;
import com.example.Pointage_Cleanic.Enum.TypeSanction;
import com.example.Pointage_Cleanic.entities.Sanction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface SanctionRepository extends MongoRepository<Sanction, String> {

    List<Sanction> findByEmployeId(String employeId);

    List<Sanction> findByEmployeIdOrderByDateSanctionDesc(String employeId);

    List<Sanction> findByType(TypeSanction type);

    List<Sanction> findByStatut(StatutSanction statut);

    List<Sanction> findByDepartement(String departement);

    List<Sanction> findByDateSanctionBetween(LocalDate debut, LocalDate fin);

    List<Sanction> findByDepartementAndDateSanctionBetween(
            String departement, LocalDate debut, LocalDate fin);

    long countByEmployeIdAndTypeAndDateSanctionBetween(
            String employeId, TypeSanction type, LocalDate debut, LocalDate fin);

    long countByEmployeIdAndDateSanctionBetween(
            String employeId, LocalDate debut, LocalDate fin);
}