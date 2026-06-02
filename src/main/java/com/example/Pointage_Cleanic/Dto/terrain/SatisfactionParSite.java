package com.example.Pointage_Cleanic.Dto.terrain;

import com.example.Pointage_Cleanic.Enum.terrain.DecisionControleTerrain;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SatisfactionParSite {
    private String siteId;
    private String siteNom;
    private double noteMoyenne;
    private long nbControles;
    private DecisionControleTerrain decisionMajoritaire;
}