package com.example.Pointage_Cleanic.entities;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.Date;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "employes")
public class Employe {

    @Id
    private Integer codeSecret;
    private String nom;
    private String prenom;
    private String numero;
    private String intervention; //agent bureau, vitre ou désinfectation
    private String site;

}
