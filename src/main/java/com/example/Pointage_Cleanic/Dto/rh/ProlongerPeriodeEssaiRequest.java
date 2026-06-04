package com.example.Pointage_Cleanic.Dto.rh;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record ProlongerPeriodeEssaiRequest(
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate nouvelleDateFin,
        String commentaire
) {
}