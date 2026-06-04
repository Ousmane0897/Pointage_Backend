package com.example.Pointage_Cleanic.Dto.rh;

import com.example.Pointage_Cleanic.Enum.rh.CategorieDocument;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record ModifierDocumentRequest(
        String nom,
        CategorieDocument categorie,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dateExpiration,
        String commentaire
) {
}
