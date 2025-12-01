package com.example.Pointage_Cleanic.entities;


import com.example.Pointage_Cleanic.Enum.RoleSuperviseur;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "superviseurs")
public class Superviseur { // Dans superviseur, on a (le ou les collecteurs de demandes et le ou les magasiniers)

    @Id
    private String id;

    private String prenom;
    private String nom;
    private String email;
    private String password;
    private String poste;
    private RoleSuperviseur role;
    private String motifDesactivation;
    private boolean active;
}
