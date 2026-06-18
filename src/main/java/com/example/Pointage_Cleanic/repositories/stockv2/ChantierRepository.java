package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.StatutChantier;
import com.example.Pointage_Cleanic.entities.stockv2.Chantier;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ChantierRepository extends MongoRepository<Chantier, String> {

    boolean existsByReference(String reference);

    List<Chantier> findByStatut(StatutChantier statut);
}
