package com.example.Pointage_Cleanic.entities.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.StatutIntervention;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "terrain_interventions")
public class FicheIntervention {

    @Id
    private String id;

    private String numero;

    private String siteId;
    private String siteCode;
    private String siteNom;

    private String affectationId;

    private String employeId;
    private String employeMatricule;
    private String employeNom;

    private LocalDateTime dateDebut;
    private LocalDateTime dateFin;
    private Integer duree;

    @Builder.Default
    private List<TacheChecklist> taches = new ArrayList<>();
    @Builder.Default
    private List<ProduitUtilise> produits = new ArrayList<>();

    private String observations;

    @Builder.Default
    private List<PhotoInterventionFichier> photos = new ArrayList<>();

    private SignatureClient signatureClient;

    private StatutIntervention statut;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}