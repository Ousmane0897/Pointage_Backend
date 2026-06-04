package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.StatutPeriodeEssai;
import com.example.Pointage_Cleanic.Enum.rh.TypeContratRh;
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
@Document(collection = "periodes_essai")
public class PeriodeEssai {

    @Id
    private String id;

    @Indexed
    private String employeId;

    private String employeNom;
    private String employePrenom;

    @Indexed
    private String contratId;

    private TypeContratRh typeContrat;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateFin;

    private Integer dureeJours;

    private StatutPeriodeEssai statut;

    @Builder.Default
    private List<AlertePeriodeEssai> alertes = new ArrayList<>();

    @Builder.Default
    private List<DecisionPeriodeEssai> decisions = new ArrayList<>();
}