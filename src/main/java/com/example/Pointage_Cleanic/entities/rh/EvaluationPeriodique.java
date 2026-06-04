package com.example.Pointage_Cleanic.entities.rh;

import com.example.Pointage_Cleanic.Enum.rh.NotationAlphabetique;
import com.example.Pointage_Cleanic.Enum.rh.StatutEvaluation;
import com.example.Pointage_Cleanic.Enum.rh.TypeNotation;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
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
@Document(collection = "evaluations_periodiques")
@CompoundIndexes({
        @CompoundIndex(name = "employe_periode_idx",
                def = "{'employeId': 1, 'periode': 1}")
})
public class EvaluationPeriodique {

    @Id
    private String id;

    @Indexed
    private String employeId;

    private String matricule;
    private String nom;
    private String prenom;
    private String departement;
    private String poste;

    private String grilleId;
    private String grilleTitre;

    private String periode;
    private TypeNotation typeNotation;

    @Builder.Default
    private List<NoteEvaluation> notesAutoEvaluation = new ArrayList<>();

    @Builder.Default
    private List<NoteEvaluation> notesManager = new ArrayList<>();

    private String commentaireEmploye;
    private String commentaireManager;
    private String objectifsPeriodeSuivante;

    private Double noteGlobale;
    private NotationAlphabetique noteAlphabetique;

    private StatutEvaluation statut;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateCreation;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateAutoEvaluation;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateEvaluationManager;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateValidation;

    private String evaluateurId;
    private String evaluateurNom;

    @Builder.Default
    private List<String> besoinsFormationIdentifies = new ArrayList<>();
}