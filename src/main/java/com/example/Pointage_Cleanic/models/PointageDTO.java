package com.example.Pointage_Cleanic.models;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PointageDTO {

    private String codeSecret;
    private String nom;
    private String prenom;
    private String date;
    private String heureArrive;
    private String heureDepart;
    private String duree;
    private String status;
    private String site;
}
