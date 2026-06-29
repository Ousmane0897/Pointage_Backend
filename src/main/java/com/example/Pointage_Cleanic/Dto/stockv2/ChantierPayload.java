package com.example.Pointage_Cleanic.Dto.stockv2;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Corps de création/modification d'un chantier. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChantierPayload {
    private String reference;
    private String nom;
    private String siteId;
    private String client;
    private String description;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateDebut;
}
