package com.example.Pointage_Cleanic.entities.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.UniteStock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "stockv2_mouvements")
public class MouvementStock {

    @Id
    private String id;

    @Indexed(unique = true)
    private String reference;

    @Indexed
    private String produitId;
    private String produitCode;
    private String produitLibelle;
    private UniteStock unite;

    private TypeMouvement type;
    private MotifMouvement motif;
    private double quantite;

    @Indexed
    private String siteSourceId;
    private String siteSourceNom;

    @Indexed
    private String siteDestinationId;
    private String siteDestinationNom;

    @Indexed
    private LocalDate date;

    /** Utilisateur créateur, déduit du JWT (jamais envoyé par le client). */
    private String utilisateur;
    private String commentaire;

    private LocalDateTime createdAt;
}
