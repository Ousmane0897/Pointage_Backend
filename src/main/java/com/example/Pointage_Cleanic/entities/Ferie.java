package com.example.Pointage_Cleanic.entities;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "feries")
public class Ferie {

    @Id
    private String id;
    private String date;
    private String nom;
}
