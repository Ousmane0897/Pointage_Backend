package com.example.Pointage_Cleanic.entities;

import com.example.Pointage_Cleanic.Enum.StatutValidation;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "demandes_validation_periode_essai")
public class DemandeValidationPeriodeEssai {

    @Id
    private String id;

    @Indexed
    private String periodeEssaiId;

    private String employeId;
    private String employeNom;
    private String employePrenom;

    private StatutValidation statut;

    private LocalDateTime dateCreation;
    private String commentaireManager;
    private String commentaireRh;
    private LocalDateTime dateFinalisation;
}