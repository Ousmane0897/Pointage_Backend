package com.example.Pointage_Cleanic.entities.stockv2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Compteur quotidien pour la génération atomique de références.
 * _id = "{PREFIXE}-yyyyMMdd" (ex: "MVT-20260617", "INV-20260617").
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_compteurs")
public class CompteurStock {

    @Id
    private String id;

    private long compteur;
}
