package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.CategorieDocument;
import com.example.Pointage_Cleanic.Enum.rh.StatutDocument;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentEmployeDto {

    private String id;
    private String employeId;
    private String employeNom;
    private String employePrenom;

    private String nom;
    private CategorieDocument categorie;

    private String fichierUrl;
    private Long tailleFichier;
    private String typeMime;

    private LocalDateTime dateUpload;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateExpiration;

    private StatutDocument statut;
    private String commentaire;
}
