package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Employe;
import com.example.Pointage_Cleanic.repositories.projections.EmployeIdProjection;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Repository
public interface EmployeRepository extends MongoRepository<Employe,String> {

    @Query(value = "{}", fields = "{ 'site' : 1 }")
    List<Employe> findAllSites(); // utilisée pour extraire les sites distincts

    default List<String> findAllDistinctSites() {
        return findAllSites().stream()
                .flatMap(e -> Arrays.stream(e.getSite()))
                .distinct()
                .collect(Collectors.toList());
    }


    Optional<Employe> findByCodeSecret(String s);

    Optional<Employe> findByAgentId(String agentId);

    Optional<Employe> findByNom(String nom);


    @Query(value = "{ 'site': ?0 }", fields = "{ '_id' : 1 }")
    List<EmployeIdProjection> findEmployeIdsBySite(String site);



}
