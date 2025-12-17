package com.example.Pointage_Cleanic.entities;


import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "utilisateur")
public class Utilisateur {

    @Id
    private String id;

    private String prenom;
    private String nom;
    private String email;
    private String password;
    private String poste;
    private RoleAdmin role;
    private boolean mustChangePassword = true;
    private Map<String, Boolean> modulesAutorises;
    private String motifDesactivation;
    private boolean active;
}
