package com.example.Pointage_Cleanic.entities;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "parametres_paie")
public class ParametresPaie {

    @Id
    private String id;

    private Double tauxIpresGeneralSalarie;
    private Double tauxIpresGeneralEmployeur;
    private Long plafondIpresGeneral;

    private Double tauxIpresComplementaireSalarie;
    private Double tauxIpresComplementaireEmployeur;
    private Long plafondIpresComplementaire;

    private Double tauxCssPrestationsFamilialesEmployeur;
    private Long plafondCss;

    private Double tauxAtMpDefaut;

    private Long montantTrimfMensuel;

    @Builder.Default
    private List<TrancheIr> baremeIr = new ArrayList<>();

    private Integer joursOuvrablesStandardMois;

    private Instant dateModification;
    private String modifieParId;
    private String modifieParNom;
}