package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.TypeSortie;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Corps de création/modification d'un bon de sortie. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BonSortiePayload {
    private TypeSortie type;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String siteSourceId;
    private DestinatairePayload destinataire;
    private String motif;
    private String demandeurId;
    private String commentaire;
    private List<LignePayload> lignes;
}
