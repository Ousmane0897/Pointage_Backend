package com.example.Pointage_Cleanic.entities.stock;


import com.example.Pointage_Cleanic.Enum.MotifMouvementSortieStock;
import com.example.Pointage_Cleanic.Enum.TypeMouvement;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "mouvement_sortie_stock")
public class MouvementSortieStock {

    @Id
    private String id;

    private String codeProduit;
    private String nomProduit;
    private TypeMouvement typeMouvement;
    private Integer quantite; // toujours positive
    private String destination; // agence ou chantier
    private MotifMouvementSortieStock motifSortieStock; // VENTE,DESTINATION_AGENCE,DON etc...
    private String responsable;
    private String beneficaire;
    private String mois;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant dateMouvement;
}
