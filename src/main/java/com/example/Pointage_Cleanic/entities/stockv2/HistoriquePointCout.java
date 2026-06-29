package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MethodeValorisation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Point d'historique du coût unitaire d'un produit (Stock v2 7.6). Un point est écrit à chaque
 * recalcul effectif (entrée valorisée en CUMP / DERNIER_PRIX). Collection séparée du produit
 * (volumétrie non bornée) pour garder le catalogue léger.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_historique_cout")
public class HistoriquePointCout {

    @Id
    private String id;

    @Indexed
    private String produitId;

    private LocalDate date;
    /** Coût unitaire en FCFA (entier) après recalcul. */
    private long cout;
    private MethodeValorisation methode;
    /** Référence du mouvement d'entrée à l'origine du recalcul (nullable). */
    private String referenceMouvement;

    private LocalDateTime createdAt;
}
