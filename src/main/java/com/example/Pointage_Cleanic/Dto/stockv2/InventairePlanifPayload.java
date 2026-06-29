package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.PerimetreInventaire;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InventairePlanifPayload {
    private String libelle;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate datePlanifiee;

    private String siteId;
    private PerimetreInventaire perimetre;
    private String categorieId;
    private List<String> produitIds;
    private double seuilEcartJustification;
    private String commentaire;
}
