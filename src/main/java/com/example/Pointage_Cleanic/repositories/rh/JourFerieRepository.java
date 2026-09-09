package com.example.Pointage_Cleanic.repositories.rh;

import com.example.Pointage_Cleanic.entities.rh.JourFerie;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JourFerieRepository extends MongoRepository<JourFerie, String> {

    /**
     * Fériés d'une période, <b>bornes incluses</b>.
     *
     * <p>C'est la seule lecture qu'effectuent les calculs (récapitulatif, congés, pointage) :
     * ils chargent l'intervalle une fois et travaillent ensuite sur un {@code Set<LocalDate>}
     * en mémoire. ⚠ Ne jamais appeler cette méthode <i>par employé</i> dans une boucle — ce
     * serait N requêtes Mongo pour une valeur identique, et des résultats incohérents entre
     * eux si l'exécution chevauchait une saisie RH. Même discipline que {@code PerimetreConges}
     * et {@code BaremeConges}.
     */
    List<JourFerie> findByDateBetween(LocalDate debut, LocalDate fin);

    Optional<JourFerie> findByDate(LocalDate date);

    boolean existsByDate(LocalDate date);
}
