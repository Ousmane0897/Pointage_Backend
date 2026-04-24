package com.example.Pointage_Cleanic.Dto;

public record DossierEmployeImportError(
        int index,
        String matricule,
        String field,
        String code,
        String message
) {}