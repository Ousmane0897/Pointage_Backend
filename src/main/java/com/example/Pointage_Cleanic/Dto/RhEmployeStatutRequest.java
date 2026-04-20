package com.example.Pointage_Cleanic.Dto;

import com.example.Pointage_Cleanic.entities.EmployeComplet;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class RhEmployeStatutRequest {

    private EmployeComplet.StatutEmploye statut;
    private String motifSortie;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dateSortie;
}