package com.example.Pointage_Cleanic.entities;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "absences")
public class Absent {

    @Id
    private String id;
    private String codeSecret;
    private String prenom;
    private String nom;
    private String numero;
    private String dateAbsence;
    private String motif;
    private String justification;
    private String intervention;
    private String[] site;
}
