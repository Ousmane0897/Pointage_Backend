package com.example.Pointage_Cleanic.Dto;

import java.util.List;

public record DossierEmployeBulkImportResponse(
        int total,
        int inserted,
        int failed,
        List<String> insertedIds,
        List<DossierEmployeImportError> errors
) {}