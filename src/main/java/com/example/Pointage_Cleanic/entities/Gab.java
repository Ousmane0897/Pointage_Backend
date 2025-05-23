package com.example.Pointage_Cleanic.entities;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "gab")
public class Gab {

    @Id
    private String id;
    private String site;
    private String intervenant;
    private String frequenceDunettoyage;
}
