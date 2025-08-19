package com.example.Pointage_Cleanic.repositories;

import com.example.Pointage_Cleanic.entities.Employe;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
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

    @Query(value = "{ 'site': ?0 }", fields = "{ '_id' : 1 }")
    List<String> findEmployeIdsBySite(String site);


}
