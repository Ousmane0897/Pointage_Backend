package com.example.Pointage_Cleanic.Dto.stockv2;

import com.example.Pointage_Cleanic.Enum.stockv2.MotifMouvement;
import com.example.Pointage_Cleanic.Enum.stockv2.TypeMouvement;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MouvementPayload {
    private String produitId;
    private TypeMouvement type;
    private MotifMouvement motif;
    private double quantite;
    private String siteSourceId;
    private String siteDestinationId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    private String commentaire;
}
