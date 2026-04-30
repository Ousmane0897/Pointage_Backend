package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.Enum.StatutContrat;
import com.example.Pointage_Cleanic.Enum.TypeContratRh;
import com.example.Pointage_Cleanic.entities.Avenant;
import com.example.Pointage_Cleanic.entities.Renouvellement;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContratDto {

    private String id;
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
    private Integer dureeEssaiMois;
    private List<Renouvellement> renouvellements;
    private List<Avenant> avenants;

    // Fichier contrat PDF (méta-données exposées, byte[] servi via endpoint dédié)
    private String fichierContratUrl;
    private String fichierContratNom;
    private String fichierContratMimeType;
}