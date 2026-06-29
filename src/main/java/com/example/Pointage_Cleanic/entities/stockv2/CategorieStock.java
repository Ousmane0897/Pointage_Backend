package com.example.Pointage_Cleanic.entities.stockv2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Catégorie de produits, arborescente (parentId null = racine, niveau 0).
 * nbEnfants / nbProduits sont calculés à la lecture, pas stockés.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_categories")
public class CategorieStock {

    @Id
    private String id;

    private String libelle;

    @Indexed
    private String parentId;

    /** 0 = racine ; calculé depuis parentId. */
    private int niveau;

    private String description;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
