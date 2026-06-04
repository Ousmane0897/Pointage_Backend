package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutPeriodeEssai;
import com.example.Pointage_Cleanic.entities.rh.PeriodeEssai;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PeriodeEssaiRepository extends MongoRepository<PeriodeEssai, String> {

    Optional<PeriodeEssai> findByContratId(String contratId);

    List<PeriodeEssai> findByEmployeId(String employeId);

    Page<PeriodeEssai> findByStatut(StatutPeriodeEssai statut, Pageable pageable);

    List<PeriodeEssai> findByStatutIn(List<StatutPeriodeEssai> statuts);

    Optional<PeriodeEssai> findFirstByEmployeIdAndStatutIn(
            String employeId, List<StatutPeriodeEssai> statuts);

    List<PeriodeEssai> findByStatutInAndDateFinLessThanEqualOrderByDateFinAsc(
            List<StatutPeriodeEssai> statuts, LocalDate dateFinMax);
}