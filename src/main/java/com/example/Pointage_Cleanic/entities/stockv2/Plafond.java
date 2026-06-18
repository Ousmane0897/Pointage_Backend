package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.GranularitePlafond;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/** Plafond de dotation mensuel d'un site, par produit ou par catégorie. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_plafonds")
public class Plafond {

    @Id
    private String id;

    @Indexed
    private String siteId;

    private GranularitePlafond granularite;

    /** produitId si granularite=PRODUIT, categorieId si granularite=CATEGORIE. */
    private String cibleId;

    private long plafondMensuel;
    private boolean actif;
    private String commentaire;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
