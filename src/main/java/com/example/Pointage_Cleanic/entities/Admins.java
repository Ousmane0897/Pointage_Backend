package com.example.Pointage_Cleanic.entities;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "superadmin")
public class Admins {

    @Id
    private String id;
    private String identifiant;
    private String prenom;
    private String nom;
    private String email;
    private String password;
    private String poste;
    private String role;
    private String motifDesactivation;
    private boolean active;
}
