package com.example.Pointage_Cleanic.entities.pieces;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Compteur d'une série de pièces (lot 1 / 1b, D4) : {@code _id = "<TYPE>-<EXERCICE>"}
 * (ex. {@code BE-2026}), un document par type et par exercice.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "compteurs_pieces")
public class CompteurPiece {

    @Id
    private String id;

    private long compteur;
}
