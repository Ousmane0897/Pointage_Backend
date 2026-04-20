package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.StatutContrat;
import com.example.Pointage_Cleanic.Enum.TypeContratRh;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "contrats")
public class Contrat {

    @Id
    private String id;

    @Indexed
    private String employeId;

    private String employeNom;
    private String employePrenom;

    private TypeContratRh typeContrat;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private StatutContrat statut;
    private String clauses;
    private Integer joursAvantAlerte;

    @Builder.Default
    private List<Renouvellement> renouvellements = new ArrayList<>();

    @Builder.Default
    private List<Avenant> avenants = new ArrayList<>();
}