package com.example.Pointage_Cleanic.Dto.productionchimie;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AjustementMpPayload {

    @NotBlank
    private String matierePremiereId;

    @NotNull
    @PositiveOrZero
    private Double nouvelleQuantite;

    @NotBlank
    private String commentaire;
}