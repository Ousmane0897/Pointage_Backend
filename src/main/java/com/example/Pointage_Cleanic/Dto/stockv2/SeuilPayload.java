package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeuilPayload {
    private String produitId;
    /** Si fourni : seuil propre au couple produit/site ; sinon seuil global du produit. */
    private String siteId;
    private double seuilAlerte;
}
