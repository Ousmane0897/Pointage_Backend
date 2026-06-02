package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.FrequencePassage;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sites_clients")
public class SiteClient {

    @Id
    private String id;

    @Indexed(unique = true)
    private String code;

    private String nom;
    private String raisonSociale;
    private String adresse;
    private String ville;
    private String pays;

    private CoordonneesGps coordonnees;
    private Integer rayonToleranceM;

    private ContactClient contactPrincipal;
    private List<ContactClient> contactsSecondaires;

    /** Champ texte libre (note / référence), distinct du fichier joint. */
    private String cahierDesCharges;

    /** Fichier PDF du cahier des charges — stocké inline, exclu du JSON. */
    @JsonIgnore
    private byte[] cahierDesChargesFichier;
    private String cahierDesChargesNomFichier;
    private String cahierDesChargesMimeType;

    private Double surfaceM2;
    private FrequencePassage frequencePassage;
    private String frequencePersonnalisee;
    private String specificites;

    private boolean actif;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}