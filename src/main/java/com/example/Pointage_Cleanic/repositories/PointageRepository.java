package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Pointage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PointageRepository extends MongoRepository<Pointage,String> {

    boolean existsByDeviceIdAndTimestampAfter(String deviceId, Instant timestamp);
    Long countByDate(LocalDate date);

    long countByDateAndIdIn(LocalDate date, List<String> ids);

    List<Pointage> findAllByDate(LocalDate date);

    // Tous les pointages d'un intervalle (récapitulatif mensuel : retards + présences).
    List<Pointage> findByDateBetween(LocalDate debut, LocalDate fin);

    List<Pointage> findByDateOrderByTimestampDesc(LocalDate date);

    Page<Pointage> findByDate(LocalDate date, Pageable pageable);

    // Un agent peut avoir plusieurs pointages le même jour (un par site). On ne
    // cherche donc plus « le » pointage du jour mais le pointage encore ouvert
    // (heureDepart == null) le plus récent, à clôturer au prochain POST.
    Optional<Pointage> findFirstByCodeSecretAndDateAndHeureDepartIsNullOrderByTimestampDesc(
            String codeSecret, LocalDate date);

    boolean existsByCodeSecretAndDate(String codeSecret, LocalDate date);



}
