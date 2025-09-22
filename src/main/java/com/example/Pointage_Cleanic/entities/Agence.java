package com.example.Pointage_Cleanic.entities;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "agences")
public class Agence {

    @Id
    private String id;
    private String nom;
    private String adresse;
    private String joursOuverture;
    private String heuresTravail;
    private Integer nombreAgentsMaximum;
    private boolean receptionEmploye;
    private boolean deplacementEmploye;
    private boolean deplacementInterne;

}
