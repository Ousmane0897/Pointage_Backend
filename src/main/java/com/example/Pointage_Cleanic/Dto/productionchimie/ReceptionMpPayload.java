package com.example.Pointage_Cleanic.Dto.productionchimie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceptionMpPayload {

    @NotBlank
    private String matierePremiereId;

    @NotNull
    @Positive
    private Double quantite;

    private String fournisseur;
    private String lotFournisseur;
    private LocalDate datePeremption;
    private String commentaire;
}