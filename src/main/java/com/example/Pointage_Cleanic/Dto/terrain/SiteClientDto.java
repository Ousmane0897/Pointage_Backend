package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.FrequencePassage;
import com.example.Pointage_Cleanic.entities.terrain.ContactClient;
import com.example.Pointage_Cleanic.entities.terrain.CoordonneesGps;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SiteClientDto {

    private String id;

    @NotBlank
    private String code;

    @NotBlank
    private String nom;

    private String raisonSociale;

    @NotBlank
    private String adresse;

    @NotBlank
    private String ville;

    private String pays;

    @NotNull
    private CoordonneesGps coordonnees;

    private Integer rayonToleranceM;

    @NotNull
    private ContactClient contactPrincipal;

    private List<ContactClient> contactsSecondaires;

    private String cahierDesCharges;
    private String cahierDesChargesUrl;
    private String cahierDesChargesNomFichier;

    private Double surfaceM2;

    @NotNull
    private FrequencePassage frequencePassage;

    private String frequencePersonnalisee;
    private String specificites;

    private boolean actif;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}