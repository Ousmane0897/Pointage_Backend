package com.example.Pointage_Cleanic.entities;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "absences")
@CompoundIndex(def = "{'codeSecret':1, 'dateAbsence':1}", unique = true)
public class Absent {

    @Id
    private String id;
    private String codeSecret;
    private String prenom;
    private String nom;
    private String numero;
    private LocalDate dateAbsence; // 2026-02-26;
    private String motif;
    private String justification;
    private String intervention;
    private String[] site;
}
