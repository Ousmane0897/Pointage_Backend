package com.example.Pointage_Cleanic.repositories.stockv2;

import com.example.Pointage_Cleanic.entities.stockv2.StockParSite;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockParSiteRepository extends MongoRepository<StockParSite, String> {
    Optional<StockParSite> findByProduitIdAndSiteId(String produitId, String siteId);
    List<StockParSite> findByProduitId(String produitId);
    List<StockParSite> findBySiteId(String siteId);
    List<StockParSite> findByProduitIdIn(Collection<String> produitIds);
}
