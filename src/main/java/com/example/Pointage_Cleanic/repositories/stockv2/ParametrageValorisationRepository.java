package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.ParametrageValorisation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ParametrageValorisationRepository extends MongoRepository<ParametrageValorisation, String> {
}
