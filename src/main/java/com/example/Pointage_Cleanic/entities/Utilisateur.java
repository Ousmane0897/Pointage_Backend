package com.example.Pointage_Cleanic.entities;


import com.example.Pointage_Cleanic.Enum.RoleAdmin;
import com.example.Pointage_Cleanic.entities.GestionModules.ModulesAutorises;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    /** Hash BCrypt : accepté en entrée, jamais sérialisé en sortie ni journalisé. */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String password;
    private String poste;
    private RoleAdmin role;
    private boolean mustChangePassword = true;
    private ModulesAutorises modulesAutorises;
    private String motifDesactivation;
    private boolean active;
}
