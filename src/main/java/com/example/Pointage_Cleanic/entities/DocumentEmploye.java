package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.CategorieDocument;
import com.example.Pointage_Cleanic.Enum.StatutDocument;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "documents_employes")
public class DocumentEmploye {

    @Id
    private String id;

    @Indexed
    private String employeId;

    private String employeNom;
    private String employePrenom;

    private String nom;
    private CategorieDocument categorie;

    private String fichierNom;
    private Long tailleFichier;
    private String typeMime;

    private LocalDateTime dateUpload;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateExpiration;

    private StatutDocument statut;
    private String commentaire;

    private byte[] fichier;
}
