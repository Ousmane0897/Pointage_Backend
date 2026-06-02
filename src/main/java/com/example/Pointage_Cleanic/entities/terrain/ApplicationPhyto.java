package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutApplicationPhyto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "applications_phyto")
public class ApplicationPhyto {

    @Id
    private String id;

    private String numero;

    private String siteId;
    private String siteCode;
    private String siteNom;

    private String employeId;
    private String employeMatricule;
    private String employeNom;

    private String produitId;
    private String produitNomCommercial;
    private String produitNumeroHomologation;

    private Double doseAppliquee;
    private String doseUnite;

    private ZoneTraitement zoneTraitee;

    private LocalDateTime dateApplication;
    private String conditionsMeteo;
    private Double temperatureC;

    private StatutApplicationPhyto statut;
    private String commentaire;

    private LocalDateTime dateFinReentree;
    private LocalDateTime dateProchaineApplicationAutorisee;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}