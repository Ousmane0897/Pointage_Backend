package com.example.Pointage_Cleanic.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.Date;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "pointages")
public class Pointage {

    @Id
    private Integer codeSecret;
    private String nom;
    private String prenom;
    private LocalDate date;
    private String heureArrive;
    private String heureDepart;
    private String duree;
    private String status;
    private String site;
}
