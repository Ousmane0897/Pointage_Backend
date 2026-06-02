package com.example.Pointage_Cleanic.Dto.terrain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterventionsParSite {
    private String siteId;
    private String siteCode;
    private String siteNom;
    private long nbInterventions;
    private long nbPrevues;
    private double tauxCouverture;
}